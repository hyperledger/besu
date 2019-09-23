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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotBlockConfirmer.ContestedPivotBlockException;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

public class PivotBlockConfirmerTest {

  private static final long PIVOT_BLOCK_NUMBER = 10;

  private ProtocolContext<Void> protocolContext;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;
  private PivotBlockConfirmer<Void> pivotBlockConfirmer;
  private ProtocolSchedule<Void> protocolSchedule;

  @Before
  public void setUp() {
    final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain, blockchainSetupUtil.getWorldArchive(), timeout::get);
    pivotBlockConfirmer = createPivotBlockConfirmer(3, 1);
  }

  private PivotBlockConfirmer<Void> createPivotBlockConfirmer(
      final int peersToQuery, final int maxRetries) {
    return pivotBlockConfirmer =
        spy(
            new PivotBlockConfirmer<>(
                protocolSchedule,
                ethProtocolManager.ethContext(),
                metricsSystem,
                PIVOT_BLOCK_NUMBER,
                peersToQuery,
                maxRetries));
  }

  @Test
  public void completeSuccessfully() {
    pivotBlockConfirmer = createPivotBlockConfirmer(2, 1);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    // First peer responds
    respondingPeerA.respond(responder);

    assertThat(future).isNotDone();

    // Second peer responds
    respondingPeerB.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void delayedResponse() {
    pivotBlockConfirmer = createPivotBlockConfirmer(2, 1);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    // First peer is responsive
    respondingPeerA.respond(responder);

    // Extra peer joins - it shouldn't be queried while other peer has outstanding request
    final RespondingEthPeer extraPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Slow peer responds
    respondingPeerB.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void peerTimesOutThenIsUnresponsive() {
    pivotBlockConfirmer = createPivotBlockConfirmer(2, 1);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    // First peer is responsive
    respondingPeerA.respond(responder);

    // Extra peer joins - it shouldn't be queried while other peer has outstanding request
    final RespondingEthPeer extraPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Peer request times out
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Peer responds with nothing
    respondingPeerB.respond(RespondingEthPeer.emptyResponder());
    // Now our extra peer should be queried
    assertThat(extraPeer.hasOutstandingRequests()).isTrue();
    extraPeer.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void peerTimesOut() {
    pivotBlockConfirmer = createPivotBlockConfirmer(2, 1);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    // First peer is responsive
    respondingPeerA.respond(responder);

    // Extra peer joins - it shouldn't be queried while other peer has outstanding request
    final RespondingEthPeer extraPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Peer responds with nothing
    respondingPeerB.respond(RespondingEthPeer.emptyResponder());
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Peer responds with nothing
    respondingPeerB.respond(RespondingEthPeer.emptyResponder());
    // Now our extra peer should be queried
    assertThat(extraPeer.hasOutstandingRequests()).isTrue();
    extraPeer.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void peerUnresponsive() {
    pivotBlockConfirmer = createPivotBlockConfirmer(2, 1);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    // First peer is responsive
    respondingPeerA.respond(responder);

    // Extra peer joins - it shouldn't be queried while other peer has outstanding request
    final RespondingEthPeer extraPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Peer request times out
    assertThat(respondingPeerB.hasOutstandingRequests()).isTrue();
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();

    // Peer request times out
    assertThat(respondingPeerB.hasOutstandingRequests()).isTrue();
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);
    // Now our extra peer should be queried
    assertThat(extraPeer.hasOutstandingRequests()).isTrue();
    extraPeer.respond(responder);

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void headerMismatch() {
    pivotBlockConfirmer = createPivotBlockConfirmer(3, 1);

    final Responder responderA =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final Responder responderB = responderForFakeBlocks(PIVOT_BLOCK_NUMBER);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();
    respondingPeerA.respond(responderA);
    assertThat(future).isNotDone();
    respondingPeerB.respond(responderB);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(ContestedPivotBlockException.class);

    // Create extra peer and make sure that it isn't queried
    final RespondingEthPeer extraPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    EthProtocolManagerTestUtil.expirePendingTimeouts(ethProtocolManager);
    assertThat(extraPeer.hasOutstandingRequests()).isFalse();
  }

  private Responder responderForFakeBlocks(final long... blockNumbers) {
    final Blockchain mockBlockchain = spy(blockchain);
    for (long blockNumber : blockNumbers) {
      when(mockBlockchain.getBlockHeader(blockNumber))
          .thenReturn(
              Optional.of(
                  new BlockHeaderTestFixture()
                      .number(blockNumber)
                      .extraData(BytesValue.of(1))
                      .buildHeader()));
    }

    return RespondingEthPeer.blockchainResponder(mockBlockchain);
  }
}
