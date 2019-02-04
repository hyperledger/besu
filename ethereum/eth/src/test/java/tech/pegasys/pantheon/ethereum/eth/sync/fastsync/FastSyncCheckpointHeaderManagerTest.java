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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

public class FastSyncCheckpointHeaderManagerTest {

  protected ProtocolSchedule<Void> protocolSchedule;
  protected EthProtocolManager ethProtocolManager;
  protected EthContext ethContext;
  protected ProtocolContext<Void> protocolContext;
  private SyncState syncState;

  private BlockDataGenerator gen;
  private BlockchainSetupUtil<Void> localBlockchainSetup;
  protected MutableBlockchain localBlockchain;
  private BlockchainSetupUtil<Void> otherBlockchainSetup;
  protected Blockchain otherBlockchain;
  private LabelledMetric<OperationTimer> ethTasksTimer;
  private BlockHeader pivotBlockHeader;
  private FastSyncCheckpointHeaderManager<Void> checkpointHeaderManager;
  private RespondingEthPeer peer;

  @Before
  public void setupTest() {
    gen = new BlockDataGenerator();
    localBlockchainSetup = BlockchainSetupUtil.forTesting();
    localBlockchain = spy(localBlockchainSetup.getBlockchain());
    otherBlockchainSetup = BlockchainSetupUtil.forTesting();
    otherBlockchain = otherBlockchainSetup.getBlockchain();

    protocolSchedule = localBlockchainSetup.getProtocolSchedule();
    protocolContext = localBlockchainSetup.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(localBlockchain, localBlockchainSetup.getWorldArchive());
    ethContext = ethProtocolManager.ethContext();
    syncState = new SyncState(protocolContext.getBlockchain(), ethContext.getEthPeers());

    ethTasksTimer = NoOpMetricsSystem.NO_OP_LABELLED_TIMER;

    otherBlockchainSetup.importFirstBlocks(30);

    pivotBlockHeader = block(17);

    peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, localBlockchain);

    checkpointHeaderManager =
        new FastSyncCheckpointHeaderManager<>(
            SynchronizerConfiguration.builder()
                .downloaderChainSegmentSize(5)
                .downloaderHeadersRequestSize(5)
                .build(),
            protocolContext,
            ethContext,
            syncState,
            protocolSchedule,
            ethTasksTimer,
            pivotBlockHeader);
  }

  @Test
  public void shouldNotRequestCheckpointHeadersBeyondThePivotBlock() {
    final SyncTarget syncTarget = syncState.setSyncTarget(peer.getEthPeer(), block(10));
    assertCheckpointHeaders(syncTarget, asList(block(10), block(15), pivotBlockHeader));
  }

  @Test
  public void shouldNotDuplicatePivotBlockAsCheckpoint() {
    final SyncTarget syncTarget = syncState.setSyncTarget(peer.getEthPeer(), block(7));
    assertCheckpointHeaders(syncTarget, asList(block(7), block(12), pivotBlockHeader));
  }

  @Test
  public void shouldHaveNoCheckpointsWhenCommonAncestorIsPivotBlock() {
    final SyncTarget syncTarget =
        syncState.setSyncTarget(peer.getEthPeer(), block(pivotBlockHeader.getNumber()));
    assertCheckpointHeaders(syncTarget, emptyList());
  }

  @Test
  public void shouldHaveNoCheckpointsWhenCommonAncestorIsAfterPivotBlock() {
    final SyncTarget syncTarget =
        syncState.setSyncTarget(peer.getEthPeer(), block(pivotBlockHeader.getNumber() + 1));
    assertCheckpointHeaders(syncTarget, emptyList());
  }

  @Test
  public void shouldHaveCommonAncestorAndPivotBlockWhenCommonAncestorImmediatelyBeforePivotBlock() {
    final BlockHeader commonAncestor = block(pivotBlockHeader.getNumber() - 1);
    final SyncTarget syncTarget = syncState.setSyncTarget(peer.getEthPeer(), commonAncestor);
    assertCheckpointHeaders(syncTarget, asList(commonAncestor, pivotBlockHeader));
  }

  private void assertCheckpointHeaders(
      final SyncTarget syncTarget, final List<BlockHeader> expected) {
    final CompletableFuture<List<BlockHeader>> result =
        checkpointHeaderManager.pullCheckpointHeaders(syncTarget);

    final Responder responder = RespondingEthPeer.blockchainResponder(otherBlockchain);
    peer.respondWhile(responder, peer::hasOutstandingRequests);

    assertThat(result).isCompletedWithValue(expected);
  }

  private BlockHeader block(final long blockNumber) {
    return otherBlockchain.getBlockHeader(blockNumber).get();
  }
}
