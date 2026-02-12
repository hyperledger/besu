/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DownloadBackwardHeadersStepTest {

  private static ProtocolContext protocolContext;
  private static ProtocolSchedule protocolSchedule;
  private static MutableBlockchain blockchain;

  private PeerTaskExecutor peerTaskExecutor;
  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;

  @BeforeAll
  public static void setUpClass() {
    final BlockchainSetupUtil setupUtil = BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    setupUtil.importFirstBlocks(20);
    protocolContext = setupUtil.getProtocolContext();
    protocolSchedule = setupUtil.getProtocolSchedule();
    blockchain = setupUtil.getBlockchain();
  }

  @BeforeEach
  public void setUp() {
    peerTaskExecutor = mock(PeerTaskExecutor.class);
    TransactionPool transactionPool = mock(TransactionPool.class);
    ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setProtocolSchedule(ProtocolScheduleFixture.TESTING_NETWORK)
            .setBlockchain(blockchain)
            .setEthScheduler(new DeterministicEthScheduler(() -> false))
            .setWorldStateArchive(protocolContext.getWorldStateArchive())
            .setTransactionPool(transactionPool)
            .setEthereumWireProtocolConfiguration(EthProtocolConfiguration.DEFAULT)
            .setPeerTaskExecutor(peerTaskExecutor)
            .build();
    ethContext = ethProtocolManager.ethContext();
  }

  @Test
  public void shouldDownloadHeadersSuccessfully() throws ExecutionException, InterruptedException {
    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 10, 0);

    final List<BlockHeader> mockHeaders = createMockHeaders(10, 100);
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(100L);

    assertThat(result.get()).hasSize(10);
    assertThat(result.get()).isEqualTo(mockHeaders);
    verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldCalculateCorrectHeadersToRequest()
      throws ExecutionException, InterruptedException {
    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 100, 50);

    // startBlock = 100, trustAnchor = 50, so remainingHeaders = 50
    // Math.min(100, 50) = 50 headers should be requested
    // First call returns 30, second call returns remaining 20
    final List<BlockHeader> firstBatch = createMockHeaders(30, 100);
    final List<BlockHeader> secondBatch = createMockHeaders(20, 70);

    final PeerTaskExecutorResult<List<BlockHeader>> firstResult =
        new PeerTaskExecutorResult<>(
            Optional.of(firstBatch), PeerTaskExecutorResponseCode.SUCCESS, Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> secondResult =
        new PeerTaskExecutorResult<>(
            Optional.of(secondBatch),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(firstResult, secondResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(100L);

    // Should collect all 50 headers
    assertThat(result.get()).hasSize(50);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldRetryOnNoPeerAvailable() throws ExecutionException, InterruptedException {
    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 5, 0);

    final List<BlockHeader> mockHeaders = createMockHeaders(5, 100);

    // First call: NO_PEER_AVAILABLE, second call: SUCCESS
    final PeerTaskExecutorResult<List<BlockHeader>> noPeerResult =
        new PeerTaskExecutorResult<>(
            Optional.empty(),
            PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE,
            Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(noPeerResult, successResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(100L);

    assertThat(result.get()).hasSize(5);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  public void shouldFailOnInternalServerError() {
    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 10, 0);

    final PeerTaskExecutorResult<List<BlockHeader>> errorResult =
        new PeerTaskExecutorResult<>(
            Optional.empty(),
            PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(errorResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(100L);

    assertThatThrownBy(() -> result.get())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to download 10 headers starting from block 100");
  }

  @Test
  public void shouldHandleSmallRemainingHeaders() throws ExecutionException, InterruptedException {
    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 100, 95);

    // startBlock = 100, trustAnchor = 95, so only 5 headers should be requested
    final List<BlockHeader> mockHeaders = createMockHeaders(5, 100);
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(100L);

    assertThat(result.get()).hasSize(5);
    verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldHandleEmptySuccessResponse() throws ExecutionException, InterruptedException {
    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 10, 90);

    // First response is empty (peer misbehaving), second has the data
    final List<BlockHeader> mockHeaders = createMockHeaders(10, 100);

    final PeerTaskExecutorResult<List<BlockHeader>> emptyResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Collections.emptyList()),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(emptyResult, successResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(100L);

    assertThat(result.get()).hasSize(10);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  public void shouldHandleVeryLargeBlockNumbers() throws ExecutionException, InterruptedException {
    final long startBlock = Long.MAX_VALUE - 1000;
    final long trustAnchor = Long.MAX_VALUE - 2000;

    final DownloadBackwardHeadersStep step =
        new DownloadBackwardHeadersStep(protocolSchedule, ethContext, 500, trustAnchor);

    final List<BlockHeader> mockHeaders = createMockHeaders(500, startBlock);
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

    final CompletableFuture<List<BlockHeader>> result = step.apply(startBlock);

    assertThat(result.get()).hasSize(500);
    verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
  }

  private List<BlockHeader> createMockHeaders(final int count, final long startBlock) {
    final BlockHeaderFunctions bhf = new LocalBlockHeaderFunctions();
    final List<BlockHeader> headers = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      headers.add(
          new BlockHeaderTestFixture()
              .number(startBlock - i)
              .blockHeaderFunctions(bhf)
              .parentHash(Hash.EMPTY)
              .buildHeader());
    }
    return headers;
  }

  static class LocalBlockHeaderFunctions implements BlockHeaderFunctions {

    @Override
    public Hash hash(final BlockHeader header) {
      return Hash.EMPTY;
    }

    @Override
    public ParsedExtraData parseExtraData(final BlockHeader header) {
      return null;
    }
  }
}
