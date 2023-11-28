/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.ForkchoiceEvent;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class FastSyncActionsTest {

  private final SynchronizerConfiguration.Builder syncConfigBuilder =
      new SynchronizerConfiguration.Builder().syncMode(SyncMode.FAST).fastSyncPivotDistance(1000);

  private final WorldStateStorageCoordinator worldStateStorageCoordinator =
      mock(WorldStateStorageCoordinator.class);
  private final AtomicInteger timeoutCount = new AtomicInteger(0);
  private SynchronizerConfiguration syncConfig = syncConfigBuilder.build();
  private FastSyncActions fastSyncActions;
  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;
  private EthPeers ethPeers;
  private MutableBlockchain blockchain;
  private BlockchainSetupUtil blockchainSetupUtil;
  private SyncState syncState;
  private MetricsSystem metricsSystem;

  static class FastSyncActionsTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setUp(final DataStorageFormat storageFormat) {
    when(worldStateStorageCoordinator.getDataStorageFormat()).thenReturn(storageFormat);
    blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            ProtocolScheduleFixture.MAINNET,
            blockchain,
            () -> timeoutCount.getAndDecrement() > 0,
            blockchainSetupUtil.getWorldArchive(),
            blockchainSetupUtil.getTransactionPool(),
            EthProtocolConfiguration.defaultConfig());
    ethContext = ethProtocolManager.ethContext();
    ethPeers = ethContext.getEthPeers();
    syncState = new SyncState(blockchain, ethPeers);
    metricsSystem = new NoOpMetricsSystem();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void waitForPeersShouldSucceedIfEnoughPeersAreFound(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    for (int i = 0; i < syncConfig.getFastSyncMinimumPeerCount(); i++) {
      EthProtocolManagerTestUtil.createPeer(
          ethProtocolManager, syncConfig.getFastSyncPivotDistance() + i + 1);
    }
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(result).isCompletedWithValue(new FastSyncState(5));
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void returnTheSamePivotBlockIfAlreadySelected(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    final FastSyncState fastSyncState = new FastSyncState(pivotHeader);
    final CompletableFuture<FastSyncState> result = fastSyncActions.selectPivotBlock(fastSyncState);
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(fastSyncState);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldUseExistingPivotBlockIfAvailable(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(new FastSyncState(pivotHeader));
    final FastSyncState expected = new FastSyncState(pivotHeader);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldSelectBlockPivotDistanceFromBestPeer(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final int minPeers = 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState expected = new FastSyncState(4000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldConsiderTotalDifficultyWhenSelectingBestPeer(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final int minPeers = 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 5500);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(2000), 4000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState expected = new FastSyncState(3000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldWaitAndRetryUntilMinHeightEstimatesAreAvailable(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    final int minPeers = 2;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    // First peer is under the threshold, we should keep retrying
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    // Second peers meets min peer threshold, we should select the pivot
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isDone();
    final FastSyncState expected = new FastSyncState(4000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldWaitAndRetryIfSufficientChainHeightEstimatesAreUnavailable(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final int minPeers = 3;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));
    final long minPivotHeight = syncConfig.getFastSyncPivotDistance() + 1L;
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    // Create peers without chain height estimates
    List<RespondingEthPeer> peers = new ArrayList<>();
    for (int i = 0; i < minPeers; i++) {
      final Difficulty td = Difficulty.of(i);
      final OptionalLong height = OptionalLong.empty();
      final RespondingEthPeer peer =
          EthProtocolManagerTestUtil.createPeer(ethProtocolManager, td, height);
      peers.add(peer);
    }

    // No pivot should be selected while peers do not have height estimates
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(result).isNotDone();
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    // Set subset of heights
    peers
        .subList(0, minPeers - 1)
        .forEach(p -> p.getEthPeer().chainState().updateHeightEstimate(minPivotHeight + 10));

    // No pivot should be selected while only a subset of peers have height estimates
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    // Set final height
    final long bestPeerHeight = minPivotHeight + 1;
    peers.get(minPeers - 1).getEthPeer().chainState().updateHeightEstimate(bestPeerHeight);
    final FastSyncState expected =
        new FastSyncState(bestPeerHeight - syncConfig.getFastSyncPivotDistance());
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldWaitAndRetryIfSufficientValidatedPeersUnavailable(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final int minPeers = 3;
    final PeerValidator validator = mock(PeerValidator.class);
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));
    final long minPivotHeight = syncConfig.getFastSyncPivotDistance() + 1L;
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    // Create peers that are not validated
    final OptionalLong height = OptionalLong.of(minPivotHeight + 10);
    List<RespondingEthPeer> peers = new ArrayList<>();
    for (int i = 0; i < minPeers; i++) {
      final Difficulty td = Difficulty.of(i);

      final RespondingEthPeer peer =
          EthProtocolManagerTestUtil.createPeer(ethProtocolManager, td, height, validator);
      peers.add(peer);
    }

    // No pivot should be selected while peers are not fully validated
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(result).isNotDone();
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    // Validate a subset of peers
    peers.subList(0, minPeers - 1).forEach(p -> p.getEthPeer().markValidated(validator));

    // No pivot should be selected while only a subset of peers has height estimates
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    // Set best height and mark best peer validated
    final long bestPeerHeight = minPivotHeight + 11;
    final EthPeer bestPeer = peers.get(minPeers - 1).getEthPeer();
    bestPeer.chainState().updateHeightEstimate(bestPeerHeight);
    bestPeer.markValidated(validator);
    final FastSyncState expected =
        new FastSyncState(bestPeerHeight - syncConfig.getFastSyncPivotDistance());
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockUsesBestPeerWithHeightEstimate(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    selectPivotBlockUsesBestPeerMatchingRequiredCriteria(true, false);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockUsesBestPeerThatIsValidated(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    selectPivotBlockUsesBestPeerMatchingRequiredCriteria(false, true);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockUsesBestPeerThatIsValidatedAndHasHeightEstimate(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    selectPivotBlockUsesBestPeerMatchingRequiredCriteria(true, true);
  }

  private void selectPivotBlockUsesBestPeerMatchingRequiredCriteria(
      final boolean bestMissingHeight, final boolean bestNotValidated) {
    final int minPeers = 3;
    final int peerCount = minPeers + 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));
    final long minPivotHeight = syncConfig.getFastSyncPivotDistance() + 1L;
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    // Create peers without chain height estimates
    final PeerValidator validator = mock(PeerValidator.class);
    List<RespondingEthPeer> peers = new ArrayList<>();
    for (int i = 0; i < peerCount; i++) {
      // Best peer by td is the first peer, td decreases as i increases
      final boolean isBest = i == 0;
      final Difficulty td = Difficulty.of(peerCount - i);

      final OptionalLong height;
      if (isBest && bestMissingHeight) {
        // Don't set a height estimate for the best peer
        height = OptionalLong.empty();
      } else {
        // Height increases with i
        height = OptionalLong.of(minPivotHeight + i);
      }

      final RespondingEthPeer peer =
          EthProtocolManagerTestUtil.createPeer(ethProtocolManager, td, height, validator);
      if (!isBest || !bestNotValidated) {
        peer.getEthPeer().markValidated(validator);
      }
      peers.add(peer);
    }

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);

    final long expectedBestChainHeight =
        peers.get(1).getEthPeer().chainState().getEstimatedHeight();
    final FastSyncState expected =
        new FastSyncState(expectedBestChainHeight - syncConfig.getFastSyncPivotDistance());
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldWaitAndRetryIfBestPeerChainIsShorterThanPivotDistance(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final int minPeers = 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));
    final long pivotDistance = syncConfig.getFastSyncPivotDistance();

    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, pivotDistance - 1);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(result).isNotDone();
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    final long validHeight = pivotDistance + 1;
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, validHeight);
    final FastSyncState expected = new FastSyncState(1);
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void selectPivotBlockShouldRetryIfBestPeerChainIsEqualToPivotDistance(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final long pivotDistance = syncConfig.getFastSyncPivotDistance();
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    // Create peers with chains that are too short
    for (int i = 0; i < syncConfig.getFastSyncMinimumPeerCount(); i++) {
      EthProtocolManagerTestUtil.createPeer(ethProtocolManager, pivotDistance);
    }

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(result).isNotDone();
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isNotDone();

    final long validHeight = pivotDistance + 1;
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, validHeight);
    final FastSyncState expected = new FastSyncState(1);
    EthProtocolManagerTestUtil.runPendingFutures(ethProtocolManager);
    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void downloadPivotBlockHeaderShouldUseExistingPivotBlockHeaderIfPresent(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    final FastSyncState expected = new FastSyncState(pivotHeader);
    assertThat(fastSyncActions.downloadPivotBlockHeader(expected)).isCompletedWithValue(expected);
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void downloadPivotBlockHeaderShouldRetrievePivotBlockHeader(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    syncConfig = SynchronizerConfiguration.builder().fastSyncMinimumPeerCount(1).build();
    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem));

    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1001);
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.downloadPivotBlockHeader(new FastSyncState(1));
    assertThat(result).isNotCompleted();

    final RespondingEthPeer.Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    peer.respond(responder);

    assertThat(result).isCompletedWithValue(new FastSyncState(blockchain.getBlockHeader(1).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(FastSyncActionsTest.FastSyncActionsTestArguments.class)
  public void downloadPivotBlockHeaderShouldRetrievePivotBlockHash(
      final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    syncConfig = SynchronizerConfiguration.builder().fastSyncMinimumPeerCount(1).build();
    GenesisConfigOptions genesisConfig = mock(GenesisConfigOptions.class);
    when(genesisConfig.getTerminalBlockNumber()).thenReturn(OptionalLong.of(10L));

    final Optional<ForkchoiceEvent> finalizedEvent =
        Optional.of(
            new ForkchoiceEvent(
                null,
                blockchain.getBlockHashByNumber(3L).get(),
                blockchain.getBlockHashByNumber(2L).get()));

    fastSyncActions =
        createFastSyncActions(
            syncConfig,
            new PivotSelectorFromSafeBlock(
                blockchainSetupUtil.getProtocolContext(),
                blockchainSetupUtil.getProtocolSchedule(),
                ethContext,
                metricsSystem,
                genesisConfig,
                () -> finalizedEvent,
                () -> {}));

    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1001);
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.downloadPivotBlockHeader(
            new FastSyncState(finalizedEvent.get().getSafeBlockHash()));
    assertThat(result).isNotCompleted();

    final RespondingEthPeer.Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    peer.respond(responder);

    assertThat(result).isCompletedWithValue(new FastSyncState(blockchain.getBlockHeader(3).get()));
  }

  private FastSyncActions createFastSyncActions(
      final SynchronizerConfiguration syncConfig, final PivotBlockSelector pivotBlockSelector) {
    final ProtocolSchedule protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    final ProtocolContext protocolContext = blockchainSetupUtil.getProtocolContext();
    final EthContext ethContext = ethProtocolManager.ethContext();
    return new FastSyncActions(
        syncConfig,
        worldStateStorageCoordinator,
        protocolSchedule,
        protocolContext,
        ethContext,
        new SyncState(blockchain, ethContext.getEthPeers(), true, Optional.empty()),
        pivotBlockSelector,
        new NoOpMetricsSystem());
  }
}
