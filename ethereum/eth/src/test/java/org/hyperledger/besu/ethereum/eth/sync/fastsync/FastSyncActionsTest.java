/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

public class FastSyncActionsTest {

  private final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
  private final SynchronizerConfiguration.Builder syncConfigBuilder =
      new SynchronizerConfiguration.Builder().syncMode(SyncMode.FAST).fastSyncPivotDistance(1000);

  private final FastSyncStateStorage fastSyncStateStorage = mock(FastSyncStateStorage.class);
  private final AtomicInteger timeoutCount = new AtomicInteger(0);
  private SynchronizerConfiguration syncConfig = syncConfigBuilder.build();
  private FastSyncActions<Void> fastSyncActions;
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;

  @Before
  public void setUp() {
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            blockchainSetupUtil.getWorldArchive(),
            () -> timeoutCount.getAndDecrement() > 0);
    fastSyncActions = createFastSyncActions(syncConfig);
  }

  @Test
  public void waitForPeersShouldSucceedIfEnoughPeersAreFound() {
    for (int i = 0; i < syncConfig.getFastSyncMinimumPeerCount(); i++) {
      EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    }
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.waitForSuitablePeers(FastSyncState.EMPTY_SYNC_STATE);
    assertThat(result).isCompletedWithValue(FastSyncState.EMPTY_SYNC_STATE);
  }

  @Test
  public void waitForPeersShouldOnlyRequireOnePeerWhenPivotBlockIsAlreadySelected() {
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    final FastSyncState fastSyncState = new FastSyncState(pivotHeader);
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.waitForSuitablePeers(fastSyncState);
    assertThat(result).isNotDone();

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    assertThat(result).isCompletedWithValue(fastSyncState);
  }

  @Test
  public void selectPivotBlockShouldUseExistingPivotBlockIfAvailable() {
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    when(fastSyncStateStorage.loadState(any(BlockHeaderFunctions.class)))
        .thenReturn(new FastSyncState(pivotHeader));
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(new FastSyncState(pivotHeader));
    final FastSyncState expected = new FastSyncState(pivotHeader);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldSelectBlockPivotDistanceFromBestPeer() {
    final int minPeers = 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions = createFastSyncActions(syncConfig);

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState expected = new FastSyncState(4000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldConsiderTotalDifficultyWhenSelectingBestPeer() {
    final int minPeers = 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions = createFastSyncActions(syncConfig);

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(1000), 5500);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(2000), 4000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(FastSyncState.EMPTY_SYNC_STATE);
    final FastSyncState expected = new FastSyncState(3000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldWaitAndRetryUntilMinHeightEstimatesAreAvailable() {
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);
    final int minPeers = 2;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions = createFastSyncActions(syncConfig);

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

  @Test
  public void selectPivotBlockShouldWaitAndRetryIfSufficientChainHeightEstimatesAreUnavailable() {
    final int minPeers = 3;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions = createFastSyncActions(syncConfig);
    final long minPivotHeight = syncConfig.getFastSyncPivotDistance() + 1L;
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    // Create peers without chain height estimates
    List<RespondingEthPeer> peers = new ArrayList<>();
    for (int i = 0; i < minPeers; i++) {
      final UInt256 td = UInt256.of(i);
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

  @Test
  public void selectPivotBlockUsesBestPeerWithHeightEstimate() {
    final int minPeers = 3;
    final int peerCount = minPeers + 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions = createFastSyncActions(syncConfig);
    final long minPivotHeight = syncConfig.getFastSyncPivotDistance() + 1L;
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    // Create peers without chain height estimates
    List<RespondingEthPeer> peers = new ArrayList<>();
    for (int i = 0; i < peerCount; i++) {
      // Best peer by td is the first peer, td decreases as i increases
      final UInt256 td = UInt256.of(peerCount - i);

      final OptionalLong height;
      if (i == 0) {
        // Don't set a height estimate for the best peer
        height = OptionalLong.empty();
      } else {
        // Height increases with i
        height = OptionalLong.of(minPivotHeight + i);
      }
      final RespondingEthPeer peer =
          EthProtocolManagerTestUtil.createPeer(ethProtocolManager, td, height);
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

  @Test
  public void selectPivotBlockShouldWaitAndRetryIfBestPeerChainIsShorterThanPivotDistance() {
    final int minPeers = 1;
    syncConfigBuilder.fastSyncMinimumPeerCount(minPeers);
    syncConfig = syncConfigBuilder.build();
    fastSyncActions = createFastSyncActions(syncConfig);
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

  @Test
  public void selectPivotBlockShouldRetryIfBestPeerChainIsEqualToPivotDistance() {
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

  @Test
  public void downloadPivotBlockHeaderShouldUseExistingPivotBlockHeaderIfPresent() {
    final BlockHeader pivotHeader = new BlockHeaderTestFixture().number(1024).buildHeader();
    final FastSyncState expected = new FastSyncState(pivotHeader);
    assertThat(fastSyncActions.downloadPivotBlockHeader(expected)).isCompletedWithValue(expected);
  }

  @Test
  public void downloadPivotBlockHeaderShouldRetrievePivotBlockHeader() {
    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1001);
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.downloadPivotBlockHeader(new FastSyncState(1));
    assertThat(result).isNotCompleted();

    final RespondingEthPeer.Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    peer.respond(responder);

    assertThat(result).isCompletedWithValue(new FastSyncState(blockchain.getBlockHeader(1).get()));
  }

  private FastSyncActions<Void> createFastSyncActions(final SynchronizerConfiguration syncConfig) {
    final ProtocolSchedule<Void> protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    final ProtocolContext<Void> protocolContext = blockchainSetupUtil.getProtocolContext();
    final EthContext ethContext = ethProtocolManager.ethContext();
    return new FastSyncActions<>(
        syncConfig,
        protocolSchedule,
        protocolContext,
        ethContext,
        new SyncState(blockchain, ethContext.getEthPeers()),
        new NoOpMetricsSystem());
  }
}
