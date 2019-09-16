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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

public class PivotBlockRetrieverTest {

  private static final long PIVOT_BLOCK_NUMBER = 10;

  private ProtocolContext<Void> protocolContext;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;
  private PivotBlockRetriever<Void> pivotBlockRetriever;

  @Before
  public void setUp() {
    final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    final ProtocolSchedule<Void> protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    ethProtocolManager =
        EthProtocolManagerTestUtil.create(
            blockchain, blockchainSetupUtil.getWorldArchive(), timeout::get);
    pivotBlockRetriever =
        new PivotBlockRetriever<>(
            protocolSchedule, ethProtocolManager.ethContext(), metricsSystem, PIVOT_BLOCK_NUMBER);
  }

  @Test
  public void shouldSucceedWhenAllPeersAgree() {
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    while (!future.isDone()) {
      respondingPeerA.respond(responder);
      respondingPeerB.respond(responder);
      respondingPeerC.respond(responder);
    }

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldIgnorePeersThatDoNotHaveThePivotBlock() {
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    while (!future.isDone()) {
      respondingPeerA.respondWhile(responder, () -> !future.isDone());
    }

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldSucceedWhenMajorityOfPeersAgree() {
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer.Responder fakeResponder = responderForFakeBlock();

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    final RespondingEthPeer respondingPeerC =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    while (!future.isDone()) {
      respondingPeerA.respond(responder);
      respondingPeerB.respond(fakeResponder);
      respondingPeerC.respond(responder);
    }

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @Test
  public void shouldFailWhenPeersReturnDifferentHeaders() {
    final RespondingEthPeer.Responder responderA =
        RespondingEthPeer.blockchainResponder(blockchain, protocolContext.getWorldStateArchive());
    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer.Responder responderB = responderForFakeBlock();
    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    // Execute task and wait for response
    final AtomicReference<Throwable> actualError = new AtomicReference<>();
    final CompletableFuture<FastSyncState> future = pivotBlockRetriever.downloadPivotBlockHeader();
    while (!future.isDone()) {
      respondingPeerA.respond(responderA);
      respondingPeerB.respond(responderB);
    }
    future.whenComplete((result, error) -> actualError.set(error));

    assertThat(future).isCompletedExceptionally();
    assertThat(actualError.get())
        .isInstanceOf(FastSyncException.class)
        .extracting(e -> ((FastSyncException) e).getError())
        .isEqualTo(FastSyncError.PIVOT_BLOCK_HEADER_MISMATCH);
  }

  private RespondingEthPeer.Responder responderForFakeBlock() {
    final Blockchain mockBlockchain = mock(Blockchain.class);
    when(mockBlockchain.getBlockHeader(PIVOT_BLOCK_NUMBER))
        .thenReturn(
            Optional.of(
                new BlockHeaderTestFixture()
                    .number(PIVOT_BLOCK_NUMBER)
                    .extraData(BytesValue.of(1))
                    .buildHeader()));
    return RespondingEthPeer.blockchainResponder(mockBlockchain);
  }
}
