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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.CHAIN_TOO_SHORT;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncState.EMPTY_SYNC_STATE;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHashFunction;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Before;
import org.junit.Test;

public class FastSyncActionsTest {

  private final SynchronizerConfiguration syncConfig =
      new SynchronizerConfiguration.Builder()
          .syncMode(SyncMode.FAST)
          .fastSyncPivotDistance(1000)
          .build();

  private final FastSyncStateStorage fastSyncStateStorage = mock(FastSyncStateStorage.class);
  private final AtomicInteger timeoutCount = new AtomicInteger(0);
  private FastSyncActions<Void> fastSyncActions;
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;

  @Before
  public void setUp() {
    final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    final ProtocolSchedule<Void> protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    final ProtocolContext<Void> protocolContext = blockchainSetupUtil.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            blockchainSetupUtil.getWorldArchive(),
            () -> timeoutCount.getAndDecrement() > 0);
    final EthContext ethContext = ethProtocolManager.ethContext();
    fastSyncActions =
        new FastSyncActions<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            ethContext,
            new SyncState(blockchain, ethContext.getEthPeers()),
            new NoOpMetricsSystem());
  }

  @Test
  public void waitForPeersShouldSucceedIfEnoughPeersAreFound() {
    for (int i = 0; i < syncConfig.getFastSyncMinimumPeerCount(); i++) {
      EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    }
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE);
    assertThat(result).isCompletedWithValue(EMPTY_SYNC_STATE);
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
    when(fastSyncStateStorage.loadState(any(BlockHashFunction.class)))
        .thenReturn(new FastSyncState(pivotHeader));
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(new FastSyncState(pivotHeader));
    final FastSyncState expected = new FastSyncState(pivotHeader);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldSelectBlockPivotDistanceFromBestPeer() {
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE);
    final FastSyncState expected = new FastSyncState(4000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldConsiderTotalDifficultyWhenSelectingBestPeer() {
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(1000), 5500);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(2000), 4000);

    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE);
    final FastSyncState expected = new FastSyncState(3000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldWaitAndRetryIfNoPeersAreAvailable() {
    final CompletableFuture<FastSyncState> result =
        fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE);
    assertThat(result).isNotDone();

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 5000);
    final FastSyncState expected = new FastSyncState(4000);
    assertThat(result).isCompletedWithValue(expected);
  }

  @Test
  public void selectPivotBlockShouldFailIfBestPeerChainIsShorterThanPivotDistance() {
    EthProtocolManagerTestUtil.createPeer(
        ethProtocolManager, syncConfig.fastSyncPivotDistance() - 1);

    assertThrowsFastSyncException(
        CHAIN_TOO_SHORT, () -> fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE));
  }

  @Test
  public void selectPivotBlockShouldFailIfBestPeerChainIsEqualToPivotDistance() {
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, syncConfig.fastSyncPivotDistance());

    assertThrowsFastSyncException(
        CHAIN_TOO_SHORT, () -> fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE));
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

    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    peer.respond(responder);

    assertThat(result).isCompletedWithValue(new FastSyncState(blockchain.getBlockHeader(1).get()));
  }

  private void assertThrowsFastSyncException(
      final FastSyncError expectedError, final ThrowingCallable callable) {
    assertThatThrownBy(callable)
        .isInstanceOf(FastSyncException.class)
        .extracting(exception -> ((FastSyncException) exception).getError())
        .isEqualTo(expectedError);
  }
}
