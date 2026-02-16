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
package org.hyperledger.besu.ethereum.eth.sync;

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
import org.hyperledger.besu.ethereum.eth.sync.HeaderBatchDownloader.Direction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HeaderBatchDownloaderTest {

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
  public void shouldDownloadHeadersReverse_singleBatch() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    final List<BlockHeader> mockHeaders = createMockHeaders(10, 100, Direction.REVERSE);
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 10, Direction.REVERSE);

    assertThat(result).hasSize(10);
    assertThat(result).isEqualTo(mockHeaders);
    verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldDownloadHeadersReverse_multipleBatches() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 30);

    // Request 50 headers with batch size 30: first batch 30, second batch 20
    // Create all 50 headers as a continuous chain, then split into batches
    final List<BlockHeader> allHeaders = createMockHeaders(50, 100, Direction.REVERSE);
    final List<BlockHeader> firstBatch = allHeaders.subList(0, 30);
    final List<BlockHeader> secondBatch = allHeaders.subList(30, 50);

    final PeerTaskExecutorResult<List<BlockHeader>> firstResult =
        new PeerTaskExecutorResult<>(
            Optional.of(new ArrayList<>(firstBatch)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> secondResult =
        new PeerTaskExecutorResult<>(
            Optional.of(new ArrayList<>(secondBatch)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(firstResult, secondResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 50, Direction.REVERSE);

    assertThat(result).hasSize(50);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldRetryOnNoPeerAvailable() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 5);

    final List<BlockHeader> mockHeaders = createMockHeaders(5, 100, Direction.REVERSE);

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

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 5, Direction.REVERSE);

    assertThat(result).hasSize(5);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  public void shouldFailOnInternalServerError() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    final PeerTaskExecutorResult<List<BlockHeader>> errorResult =
        new PeerTaskExecutorResult<>(
            Optional.empty(),
            PeerTaskExecutorResponseCode.INTERNAL_SERVER_ERROR,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(errorResult);

    assertThatThrownBy(() -> downloader.downloadHeaders(100L, 10, Direction.REVERSE))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to download 10 headers starting from block 100");
  }

  @Test
  public void shouldThrowOnInvalidBatchSize() {
    assertThatThrownBy(() -> new HeaderBatchDownloader(protocolSchedule, ethContext, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize must be >= 1");
  }

  @Test
  public void shouldThrowOnInvalidCount() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    assertThatThrownBy(() -> downloader.downloadHeaders(100L, 0, Direction.REVERSE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("count must be >= 1");
  }

  @Test
  public void shouldDownloadHeadersForward_singleBatch() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 10);

    final List<BlockHeader> mockHeaders = createMockHeaders(10, 100, Direction.FORWARD);
    final PeerTaskExecutorResult<List<BlockHeader>> successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(mockHeaders),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class))).thenReturn(successResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 10, Direction.FORWARD);

    assertThat(result).hasSize(10);
    assertThat(result.getFirst().getNumber()).isEqualTo(100);
    assertThat(result.getLast().getNumber()).isEqualTo(109);
    verify(peerTaskExecutor, times(1)).execute(any(GetHeadersFromPeerTask.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldDownloadHeadersForward_multipleBatches() {
    final HeaderBatchDownloader downloader =
        new HeaderBatchDownloader(protocolSchedule, ethContext, 30);

    // Request 50 headers with batch size 30: first batch 30, second batch 20
    // Create all 50 headers as a continuous chain, then split into batches
    final List<BlockHeader> allHeaders = createMockHeaders(50, 100, Direction.FORWARD);
    final List<BlockHeader> firstBatch = allHeaders.subList(0, 30);
    final List<BlockHeader> secondBatch = allHeaders.subList(30, 50);

    final PeerTaskExecutorResult<List<BlockHeader>> firstResult =
        new PeerTaskExecutorResult<>(
            Optional.of(new ArrayList<>(firstBatch)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());
    final PeerTaskExecutorResult<List<BlockHeader>> secondResult =
        new PeerTaskExecutorResult<>(
            Optional.of(new ArrayList<>(secondBatch)),
            PeerTaskExecutorResponseCode.SUCCESS,
            Collections.emptyList());

    when(peerTaskExecutor.execute(any(GetHeadersFromPeerTask.class)))
        .thenReturn(firstResult, secondResult);

    final List<BlockHeader> result = downloader.downloadHeaders(100L, 50, Direction.FORWARD);

    assertThat(result).hasSize(50);
    verify(peerTaskExecutor, times(2)).execute(any(GetHeadersFromPeerTask.class));
  }

  private List<BlockHeader> createMockHeaders(
      final int count, final long startBlock, final Direction direction) {
    final BlockHeaderFunctions bhf = new LocalBlockHeaderFunctions();

    if (direction == Direction.FORWARD) {
      // For FORWARD: header[i].parentHash = header[i-1].hash
      // Build in order: block N, N+1, N+2, ...
      final List<BlockHeader> headers = new ArrayList<>();
      Hash parentHash = Hash.EMPTY;
      for (int i = 0; i < count; i++) {
        long blockNumber = startBlock + i;
        BlockHeader header =
            new BlockHeaderTestFixture()
                .number(blockNumber)
                .blockHeaderFunctions(bhf)
                .parentHash(parentHash)
                .buildHeader();
        headers.add(header);
        parentHash = header.getHash();
      }
      return headers;
    } else {
      // For REVERSE: header[i].parentHash = header[i+1].hash
      // Build in reverse order first (lowest to highest), then reverse the list
      // So we build: block N-count+1, N-count+2, ..., N
      // Then reverse to get: block N, N-1, N-2, ..., N-count+1
      final List<BlockHeader> headers = new ArrayList<>();
      Hash parentHash = Hash.EMPTY;
      for (int i = count - 1; i >= 0; i--) {
        long blockNumber = startBlock - i;
        BlockHeader header =
            new BlockHeaderTestFixture()
                .number(blockNumber)
                .blockHeaderFunctions(bhf)
                .parentHash(parentHash)
                .buildHeader();
        headers.addFirst(header); // Add at beginning to build in reverse order
        parentHash = header.getHash();
      }
      return headers;
    }
  }

  static class LocalBlockHeaderFunctions implements BlockHeaderFunctions {
    @Override
    public Hash hash(final BlockHeader header) {
      final String hashInput = header.getParentHash().toHexString() + header.getNumber();
      return Hash.hash(Bytes.wrap(hashInput.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public ParsedExtraData parseExtraData(final BlockHeader header) {
      return null;
    }
  }
}
