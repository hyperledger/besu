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
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotBlockConfirmer.ContestedPivotBlockException;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mockito;

public class PivotBlockConfirmerTest {

  private static final long PIVOT_BLOCK_NUMBER = 10;

  private ProtocolContext protocolContext;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final AtomicBoolean timeout = new AtomicBoolean(false);
  private EthProtocolManager ethProtocolManager;
  private MutableBlockchain blockchain;
  private TransactionPool transactionPool;
  private ProtocolSchedule protocolSchedule;
  private PeerTaskExecutor peerTaskExecutor;

  static class PivotBlockConfirmerTestArguments implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(DataStorageFormat.BONSAI), Arguments.of(DataStorageFormat.FOREST));
    }
  }

  public void setUp(final DataStorageFormat storageFormat) {
    final BlockchainSetupUtil blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(protocolSchedule)
            .setBlockchain(blockchain)
            .setEthScheduler(new DeterministicEthScheduler(timeout::get))
            .setWorldStateArchive(blockchainSetupUtil.getWorldArchive())
            .setTransactionPool(transactionPool)
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
  }

  private PivotBlockConfirmer createPivotBlockConfirmer(
      final int peersToQuery, final int maxRetries, final boolean isPeerTaskSystemEnabled) {
    return spy(
        new PivotBlockConfirmer(
            protocolSchedule,
            ethProtocolManager.ethContext(),
            metricsSystem,
            SynchronizerConfiguration.builder()
                .isPeerTaskSystemEnabled(isPeerTaskSystemEnabled)
                .build(),
            PIVOT_BLOCK_NUMBER,
            peersToQuery,
            maxRetries));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void completeSuccessfully(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, false);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
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

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void completeSuccessfullyWithPeerTask(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, true);

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.of(respondingPeerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.of(respondingPeerB.getEthPeer())));

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    future.join();
    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void delayedResponse(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, false);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
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

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void peerTimesOutThenIsUnresponsive(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, false);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);

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

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void peerTimesOut(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, false);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);

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

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void peerTimesOutUsingPeerTaskSystem(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, true);

    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);
    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    when(peerTaskExecutor.executeAgainstPeer(
            Mockito.any(GetHeadersFromPeerTask.class), Mockito.any(EthPeer.class)))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.empty()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(), PeerTaskExecutorResponseCode.TIMEOUT, Optional.empty()))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.empty()));

    // Execute task
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    assertThat(future)
        .isCompletedWithValue(
            new FastSyncState(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get()));
  }

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void peerUnresponsive(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(2, 2, false);

    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);

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

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void headerMismatch(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(3, 2, false);

    final Responder responderA =
        RespondingEthPeer.blockchainResponder(
            blockchain, protocolContext.getWorldStateArchive(), transactionPool);
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

  @ParameterizedTest
  @ArgumentsSource(PivotBlockConfirmerTestArguments.class)
  public void headerMismatchUsingPeerTaskSystem(final DataStorageFormat storageFormat) {
    setUp(storageFormat);
    PivotBlockConfirmer pivotBlockConfirmer = createPivotBlockConfirmer(3, 2, true);

    final RespondingEthPeer respondingPeerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    final RespondingEthPeer respondingPeerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 1000);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerA.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(List.of(blockchain.getBlockHeader(PIVOT_BLOCK_NUMBER).get())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.of(respondingPeerA.getEthPeer())));
    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class),
                Mockito.eq(respondingPeerB.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.of(
                    List.of(
                        new BlockHeaderTestFixture()
                            .number(PIVOT_BLOCK_NUMBER)
                            .extraData(Bytes.of(1))
                            .buildHeader())),
                PeerTaskExecutorResponseCode.SUCCESS,
                Optional.of(respondingPeerB.getEthPeer())));

    // Execute task and wait for response
    final CompletableFuture<FastSyncState> future = pivotBlockConfirmer.confirmPivotBlock();

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasRootCauseInstanceOf(ContestedPivotBlockException.class);
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

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}
