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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.ExceptionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PivotBlockRetrieverTest {

  private static final long PIVOT_BLOCK_NUMBER = 10;

  private ProtocolContext protocolContext;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;
  private TransactionPool transactionPool;
  private PivotBlockRetriever pivotBlockRetriever;
  private ProtocolSchedule protocolSchedule;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{DataStorageFormat.BONSAI}, {DataStorageFormat.FOREST}});
  }

  private final DataStorageFormat storageFormat;

  public PivotBlockRetrieverTest(final DataStorageFormat storageFormat) {
    this.storageFormat = storageFormat;
  }

  @Before
  public void setUp() {
    final BlockchainSetupUtil blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain,
            timeout::get,
            blockchainSetupUtil.getWorldArchive(),
            transactionPool,
            EthProtocolConfiguration.defaultConfig());

    pivotBlockRetriever = createPivotBlockRetriever(3, 1, 1);
  }

  private PivotBlockRetriever createPivotBlockRetriever(
      final int peersToQuery, final long pivotBlockDelta, final int maxRetries) {
    return pivotBlockRetriever =
        spy(
            new PivotBlockRetriever(
                protocolSchedule,
                ethProtocolManager.ethContext(),
                metricsSystem,
                PIVOT_BLOCK_NUMBER,
                peersToQuery,
                pivotBlockDelta,
                maxRetries));
  }

  @Test
  public void shouldSucceedWhenAllPeersAgree() {
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    respondingPeerA.respond(responder);
    respondingPeerB.respond(responder);
    respondingPeerC.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldIgnorePeersThatDoNotHaveThePivotBlock() {
    pivotBlockRetriever = createPivotBlockRetriever(3, 1, 1);
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer badPeerA = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);
    final RespondingEthPeer badPeerB = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    respondingPeerA.respond(responder);

    // With only one peer with sufficient height, we should not be done yet
    assertThat(future).isNotCompleted();

    // Check that invalid peers were not queried
    assertThat(badPeerA.hasOutstandingRequests()).isFalse();
    assertThat(badPeerB.hasOutstandingRequests()).isFalse();

    // Add new peer that we can query
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    respondingPeerB.respond(responder);

    // We need one more responsive peer before we're done
    assertThat(future).isNotCompleted();
    assertThat(badPeerA.hasOutstandingRequests()).isFalse();
    assertThat(badPeerB.hasOutstandingRequests()).isFalse();

    // Add new peer that we can query
    final RespondingEthPeer respondingPeerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    respondingPeerC.respond(responder);
    assertThat(badPeerA.hasOutstandingRequests()).isFalse();
    assertThat(badPeerB.hasOutstandingRequests()).isFalse();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldIgnorePeersThatAreNotFullyValidated() {
    final PeerValidator peerValidator = mock(PeerValidator.class);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000, peerValidator);
    final RespondingEthPeer badPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000, peerValidator);
    final RespondingEthPeer badPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000, peerValidator);

    // Mark peerA valid
    respondingPeerA.getEthPeer().markValidated(peerValidator);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    respondingPeerA.respond(responder);

    // With only one peer with sufficient height, we should not be done yet
    assertThat(future).isNotCompleted();

    // Check that invalid peers were not queried
    assertThat(badPeerA.hasOutstandingRequests()).isFalse();
    assertThat(badPeerB.hasOutstandingRequests()).isFalse();

    // Add new peer that we can query
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000, peerValidator);
    respondingPeerB.getEthPeer().markValidated(peerValidator);
    // When our new peer "connects", it is not yet valid, so we need to expire our retry timeout
    // so that the peer will get re-processed
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);

    assertThat(respondingPeerB.hasOutstandingRequests()).isTrue();
    respondingPeerB.respond(responder);

    // We need one more responsive peer before we're done
    assertThat(future).isNotCompleted();
    assertThat(badPeerA.hasOutstandingRequests()).isFalse();
    assertThat(badPeerB.hasOutstandingRequests()).isFalse();

    // Add new peer that we can query
    final RespondingEthPeer respondingPeerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000, peerValidator);
    respondingPeerC.getEthPeer().markValidated(peerValidator);
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);

    assertThat(respondingPeerC.hasOutstandingRequests()).isTrue();
    respondingPeerC.respond(responder);
    assertThat(badPeerA.hasOutstandingRequests()).isFalse();
    assertThat(badPeerB.hasOutstandingRequests()).isFalse();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldQueryBestPeersFirst() {
    pivotBlockRetriever = createPivotBlockRetriever(2, 1, 1);
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);

    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 1000);
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(500), 500);
    final RespondingEthPeer peerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 1000);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();

    peerA.respond(responder);
    peerC.respond(responder);

    // Peers A and C should be queried because they have better chain stats
    assertThat(peerB.hasOutstandingRequests()).isFalse();
    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldRecoverFromUnresponsivePeer() {
    pivotBlockRetriever = createPivotBlockRetriever(2, 1, 1);
    EthProtocolManagerTestUtil.disableEthSchedulerAutoRun(ethProtocolManager);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final Responder emptyResponder = RespondingEthPeer.emptyResponder();

    final RespondingEthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 1000);
    final RespondingEthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(1000), 1000);
    final RespondingEthPeer peerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, Difficulty.of(500), 500);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    peerA.respond(responder);
    peerB.respondTimes(emptyResponder, 2);

    // PeerA should have responded, while peerB is being retried, peerC shouldn't have been queried
    // yet
    assertThat(future).isNotCompleted();
    assertThat(peerC.hasOutstandingRequests()).isFalse();

    // After exhausting retries for peerB, we should try peerC
    peerB.respondTimes(emptyResponder, 2);
    peerC.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldRetryWhenPeersDisagreeOnPivot_successfulRetry() {
    final long pivotBlockDelta = 1;
    pivotBlockRetriever = createPivotBlockRetriever(2, pivotBlockDelta, 1);

    final Responder responderA =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Only return inconsistent block on the first round
    final Responder responderB = responderForFakeBlocks(PIVOT_BLOCK_NUMBER);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    respondingPeerA.respond(responderA);
    respondingPeerB.respond(responderB);

    // When disagreement is detected, we should push the pivot block back and retry
    final long newPivotBlock = PIVOT_BLOCK_NUMBER - 1;
    assertThat(future).isNotCompleted();
    assertThat(pivotBlockRetriever.pivotBlockNumber).hasValue(newPivotBlock);

    // Another round should reach consensus
    respondingPeerA.respond(responderA);
    respondingPeerB.respond(responderB);

    assertThat(future)
        .isCompletedWithValue(new FastSyncState(blockchain.getBlockHeader(newPivotBlock).get()));
  }

  @Test
  public void shouldRetryWhenPeersDisagreeOnPivot_exceedMaxRetries() {
    final long pivotBlockDelta = 1;
    pivotBlockRetriever = createPivotBlockRetriever(2, pivotBlockDelta, 1);

    final Responder responderA =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final Responder responderB =
        responderForFakeBlocks(PIVOT_BLOCK_NUMBER, PIVOT_BLOCK_NUMBER - pivotBlockDelta);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    respondingPeerA.respond(responderA);
    respondingPeerB.respond(responderB);

    // When disagreement is detected, we should push the pivot block back and retry
    final long newPivotBlock = PIVOT_BLOCK_NUMBER - 1;
    assertThat(future).isNotCompleted();
    assertThat(pivotBlockRetriever.pivotBlockNumber).hasValue(newPivotBlock);

    // Another round of invalid responses should lead to failure
    respondingPeerA.respond(responderA);
    respondingPeerB.respond(responderB);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasRootCauseInstanceOf(FastSyncException.class)
        .extracting(e -> ((FastSyncException) ExceptionUtils.rootCause(e)).getError())
        .isEqualTo(FastSyncError.PIVOT_BLOCK_HEADER_MISMATCH);
  }

  @Test
  public void shouldRetryWhenPeersDisagreeOnPivot_pivotInvalidOnRetry() {
    final long pivotBlockDelta = PIVOT_BLOCK_NUMBER + 1;
    pivotBlockRetriever = createPivotBlockRetriever(2, pivotBlockDelta, 1);

    final Responder responderA =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final Responder responderB = responderForFakeBlocks(PIVOT_BLOCK_NUMBER);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    respondingPeerA.respond(responderA);
    respondingPeerB.respond(responderB);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasRootCauseInstanceOf(FastSyncException.class)
        .extracting(e -> ((FastSyncException) ExceptionUtils.rootCause(e)).getError())
        .isEqualTo(FastSyncError.PIVOT_BLOCK_HEADER_MISMATCH);
  }

  private Responder responderForFakeBlocks(final long... blockNumbers) {
    final Blockchain mockBlockchain = spy(blockchain);
    for (long blockNumber : blockNumbers) {
      when(mockBlockchain.getBlockHeader(blockNumber))
          .thenReturn(
              Optional.of(
                  new BlockHeaderTestFixture()
                      .number(blockNumber)
                      .extraData(Bytes.of(1))
                      .buildHeader()));
    }

    return RespondingEthPeer.blockchainResponder(mockBlockchain);
  }
}
