/*
 * Copyright contributors to Besu.
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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration.ETH69_RECEIPT_CONFIGURATION;
import static org.hyperledger.besu.ethereum.eth.core.Utils.blocksToSyncBlocks;
import static org.hyperledger.besu.ethereum.eth.core.Utils.receiptsToSyncReceipts;
import static org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode.NO_PEER_AVAILABLE;
import static org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode.PEER_DISCONNECTED;
import static org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode.SUCCESS;
import static org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode.TIMEOUT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.SyncTransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetSyncReceiptsFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.math.BigInteger;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DownloadSyncReceiptsStepTest {

  @Mock private EthContext ethContext;
  @Mock private PeerTaskExecutor peerTaskExecutor;
  @Mock private SyncTransactionReceiptEncoder syncTransactionReceiptEncoder;
  private ProtocolSchedule protocolSchedule;
  private final BlockDataGenerator gen = new BlockDataGenerator();

  private DownloadSyncReceiptsStep downloadSyncReceiptsStep;

  @BeforeEach
  public void setUp() {
    protocolSchedule = new DefaultProtocolSchedule(Optional.of(BigInteger.ONE));
    when(ethContext.getScheduler()).thenReturn(new DeterministicEthScheduler());
    when(ethContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);

    downloadSyncReceiptsStep =
        new DownloadSyncReceiptsStep(
            protocolSchedule, ethContext, syncTransactionReceiptEncoder, Duration.ofMinutes(1));
  }

  @Test
  public void shouldDownloadReceiptsForBlocksWithTransactions()
      throws ExecutionException, InterruptedException {

    // skip genesis block, since we do not need to retrieve receipt for it
    final List<Block> blockWithTxs = gen.blockSequence(3).subList(1, 3);

    final var receiptsPerBlock =
        blockWithTxs.stream()
            .map(gen::receipts)
            .map(rs -> receiptsToSyncReceipts(rs, ETH69_RECEIPT_CONFIGURATION))
            .toList();
    final var syncBlocks = blocksToSyncBlocks(blockWithTxs);

    final Map<SyncBlock, List<SyncTransactionReceipt>> returnedReceiptsByBlock = new HashMap<>();
    for (int i = 0; i < syncBlocks.size(); i++) {
      returnedReceiptsByBlock.put(syncBlocks.get(i), receiptsPerBlock.get(i));
    }

    // Mock the peer task executor to return receipts for both blocks
    final var executorResult =
        new PeerTaskExecutorResult<>(Optional.of(returnedReceiptsByBlock), SUCCESS, emptyList());
    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(executorResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should return all blocks with receipts
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(2);
    for (int i = 0; i < blocksWithReceipts.size(); i++) {
      assertThat(blocksWithReceipts.get(i).getBlock()).isEqualTo(syncBlocks.get(i));
      assertThat(blocksWithReceipts.get(i).getReceipts()).isEqualTo(receiptsPerBlock.get(i));
    }

    // Verify the task was executed once
    verify(peerTaskExecutor, times(1)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldSkipDownloadForBlocksWithEmptyReceiptsRoot()
      throws ExecutionException, InterruptedException {

    final Block block1_withTxs = gen.blockSequence(gen.genesisBlock(), 1).getFirst();

    gen.setBlockOptionsSupplier(
        () -> BlockOptions.create().hasTransactions(false).setReceiptsRoot(Hash.EMPTY_TRIE_HASH));
    final Block block2_withoutTxs = gen.blockSequence(block1_withTxs, 1).getFirst();

    gen.setBlockOptionsSupplier(() -> BlockOptions.create().hasTransactions(true));
    final Block block3_withTxs = gen.blockSequence(block2_withoutTxs, 1).getFirst();

    final var blocks = List.of(block1_withTxs, block2_withoutTxs, block3_withTxs);

    // we must not request receipt for block2, so only return receipts for the 2 blocks with txs
    final var receiptsForBlock1 =
        receiptsToSyncReceipts(gen.receipts(block1_withTxs), ETH69_RECEIPT_CONFIGURATION);
    final var receiptsForBlock3 =
        receiptsToSyncReceipts(gen.receipts(block3_withTxs), ETH69_RECEIPT_CONFIGURATION);

    final var syncBlocks = blocksToSyncBlocks(blocks);

    final Map<SyncBlock, List<SyncTransactionReceipt>> returnedReceiptsByBlock =
        Map.of(syncBlocks.get(0), receiptsForBlock1, syncBlocks.get(2), receiptsForBlock3);

    final var executorResult =
        new PeerTaskExecutorResult<>(Optional.of(returnedReceiptsByBlock), SUCCESS, emptyList());
    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(executorResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should also return block with empty receipts
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(3);

    assertThat(blocksWithReceipts.get(0).getBlock()).isEqualTo(syncBlocks.get(0));
    assertThat(blocksWithReceipts.get(0).getReceipts()).isEqualTo(receiptsForBlock1);

    assertThat(blocksWithReceipts.get(1).getBlock()).isEqualTo(syncBlocks.get(1));
    assertThat(blocksWithReceipts.get(1).getReceipts()).isEmpty();

    assertThat(blocksWithReceipts.get(2).getBlock()).isEqualTo(syncBlocks.get(2));
    assertThat(blocksWithReceipts.get(2).getReceipts()).isEqualTo(receiptsForBlock3);

    // Verify the task was executed once
    verify(peerTaskExecutor, times(1)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldRetryUntilAllReceiptsDownloaded()
      throws ExecutionException, InterruptedException {
    // Given: 3 blocks with transactions
    final List<Block> blocks = gen.blockSequence(4).subList(1, 4);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    final var receiptsPerBlock =
        blocks.stream()
            .map(gen::receipts)
            .map(rs -> receiptsToSyncReceipts(rs, ETH69_RECEIPT_CONFIGURATION))
            .toList();

    // First call returns first block only
    final var firstExecutorResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Map.of(syncBlocks.get(0), receiptsPerBlock.get(0))), SUCCESS, emptyList());

    // Second call returns second block only
    final var secondExecutorResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Map.of(syncBlocks.get(1), receiptsPerBlock.get(1))), SUCCESS, emptyList());

    // Third call returns third block only
    final var thirdExecutorResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Map.of(syncBlocks.get(2), receiptsPerBlock.get(2))), SUCCESS, emptyList());

    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(firstExecutorResult)
        .thenReturn(secondExecutorResult)
        .thenReturn(thirdExecutorResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should return all blocks with receipts
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(3);
    for (int i = 0; i < blocksWithReceipts.size(); i++) {
      assertThat(blocksWithReceipts.get(i).getReceipts()).isEqualTo(receiptsPerBlock.get(i));
    }

    // Verify the task was executed three times
    verify(peerTaskExecutor, times(3)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void combineBlocksAndReceiptsShouldThrowWhenReceiptCountMismatch() {
    // Given: a block with transactions and fewer receipts than transactions
    gen.setBlockOptionsSupplier(
        () -> BlockOptions.create().hasTransactions(true).transactionCount(3));
    final Block block = gen.block();
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(List.of(block));

    final List<SyncTransactionReceipt> allReceipts =
        receiptsToSyncReceipts(gen.receipts(block), ETH69_RECEIPT_CONFIGURATION);

    // Add fewer receipts than transactions
    final Map<Hash, List<SyncTransactionReceipt>> receiptsByRootHash =
        Map.of(
            syncBlocks.get(0).getHeader().getReceiptsRoot(),
            allReceipts.subList(0, allReceipts.size() - 1));

    // When/Then: should throw IllegalStateException
    assertThatThrownBy(
            () -> downloadSyncReceiptsStep.combineBlocksAndReceipts(syncBlocks, receiptsByRootHash))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("incorrect number of receipts returned");
  }

  @Test
  public void combineBlocksAndReceiptsShouldReturnEmptyReceiptsWhenNotInMap() {
    // Given: blocks without transactions and without receipts in map
    gen.setBlockOptionsSupplier(() -> BlockOptions.create().hasTransactions(false));
    final Block blockWithoutTxs = gen.block();
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(List.of(blockWithoutTxs));

    final Map<Hash, List<SyncTransactionReceipt>> receiptsByRootHash = Map.of();

    // When: combining blocks and receipts
    final List<SyncBlockWithReceipts> result =
        downloadSyncReceiptsStep.combineBlocksAndReceipts(syncBlocks, receiptsByRootHash);

    // Then: should return blocks with empty receipts
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getReceipts()).isEmpty();
  }

  @Test
  public void shouldRetryAfterSingleFailureAndEventuallySucceed()
      throws ExecutionException, InterruptedException {
    // Given: 2 blocks with transactions
    final List<Block> blocks = gen.blockSequence(3).subList(1, 3);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    final var receiptsPerBlock =
        blocks.stream()
            .map(gen::receipts)
            .map(rs -> receiptsToSyncReceipts(rs, ETH69_RECEIPT_CONFIGURATION))
            .toList();

    final Map<SyncBlock, List<SyncTransactionReceipt>> returnedReceiptsByBlock = new HashMap<>();
    for (int i = 0; i < syncBlocks.size(); i++) {
      returnedReceiptsByBlock.put(syncBlocks.get(i), receiptsPerBlock.get(i));
    }

    // First call returns failure (e.g., peer disconnected)
    final var failureResult =
        new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
            Optional.empty(), PEER_DISCONNECTED, emptyList());

    // Second call returns success with all receipts
    final var successResult =
        new PeerTaskExecutorResult<>(Optional.of(returnedReceiptsByBlock), SUCCESS, emptyList());

    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(failureResult)
        .thenReturn(successResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should eventually succeed after retry
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(2);
    for (int i = 0; i < blocksWithReceipts.size(); i++) {
      assertThat(blocksWithReceipts.get(i).getBlock()).isEqualTo(syncBlocks.get(i));
      assertThat(blocksWithReceipts.get(i).getReceipts()).isEqualTo(receiptsPerBlock.get(i));
    }

    // Verify the task was executed twice (1 failure + 1 success)
    verify(peerTaskExecutor, times(2)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldRetryMultipleTimesAfterConsecutiveFailures()
      throws ExecutionException, InterruptedException {
    // Given: 1 block with transactions
    final List<Block> blocks = gen.blockSequence(2).subList(1, 2);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    final var receiptsPerBlock =
        blocks.stream()
            .map(gen::receipts)
            .map(rs -> receiptsToSyncReceipts(rs, ETH69_RECEIPT_CONFIGURATION))
            .toList();

    // First three calls return different failures
    final var timeoutResult =
        new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
            Optional.empty(), TIMEOUT, emptyList());
    final var noPeerResult =
        new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
            Optional.empty(), NO_PEER_AVAILABLE, emptyList());
    final var disconnectedResult =
        new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
            Optional.empty(), PEER_DISCONNECTED, emptyList());

    // Fourth call returns success
    final var successResult =
        new PeerTaskExecutorResult<>(
            Optional.of(Map.of(syncBlocks.getFirst(), receiptsPerBlock.getFirst())),
            SUCCESS,
            emptyList());

    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(timeoutResult)
        .thenReturn(noPeerResult)
        .thenReturn(disconnectedResult)
        .thenReturn(successResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should eventually succeed after multiple retries
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(1);
    assertThat(blocksWithReceipts.getFirst().getBlock()).isEqualTo(syncBlocks.getFirst());
    assertThat(blocksWithReceipts.getFirst().getReceipts()).isEqualTo(receiptsPerBlock.getFirst());

    // Verify the task was executed 4 times (3 failures + 1 success)
    verify(peerTaskExecutor, times(4)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldThrowIllegalStateExceptionWhenSuccessWithEmptyResult() {
    // Given: 1 block with transactions
    final List<Block> blocks = gen.blockSequence(2).subList(1, 2);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    // Mock returns SUCCESS but with empty Optional (should never happen in practice)
    final var invalidResult =
        new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
            Optional.empty(), SUCCESS, emptyList());

    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(invalidResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should throw IllegalStateException (wrapped in ExecutionException and
    // CompletionException)
    assertThatThrownBy(result::get)
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Task validation failure, it must flag empty result as failure");

    // Verify the task was executed once
    verify(peerTaskExecutor, times(1)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldDeduplicateBlocksWithSameReceiptRoot()
      throws ExecutionException, InterruptedException {
    // Given: Create 3 blocks where block2 has the same receipt root as block1.
    // The dedup in prepareRequest sends only one request per unique receipt root, so block2 is
    // excluded from the request. combineBlocksAndReceipts looks up by receipt root hash, so block2
    // still gets block1's receipts.
    final List<Block> allBlocks = gen.blockSequence(4).subList(1, 4);
    final Block block1 = allBlocks.get(0);
    final Block block3 = allBlocks.get(2);

    gen.setBlockOptionsSupplier(
        () ->
            BlockOptions.create()
                .hasTransactions(true)
                .setReceiptsRoot(block1.getHeader().getReceiptsRoot()));
    final Block block2WithSameRoot = gen.blockSequence(block1, 1).getFirst();

    final List<Block> blocks = List.of(block1, block2WithSameRoot, block3);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    // Only 2 unique receipt requests should be made (for block1 and block3)
    final var receiptsForBlock1 =
        receiptsToSyncReceipts(gen.receipts(block1), ETH69_RECEIPT_CONFIGURATION);
    final var receiptsForBlock3 =
        receiptsToSyncReceipts(gen.receipts(block3), ETH69_RECEIPT_CONFIGURATION);

    final Map<SyncBlock, List<SyncTransactionReceipt>> returnedReceiptsByBlock =
        Map.of(syncBlocks.get(0), receiptsForBlock1, syncBlocks.get(2), receiptsForBlock3);

    final var executorResult =
        new PeerTaskExecutorResult<>(Optional.of(returnedReceiptsByBlock), SUCCESS, emptyList());
    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(executorResult);

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should return all 3 blocks with receipts
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(3);

    // block1 should have its receipts
    assertThat(blocksWithReceipts.get(0).getBlock()).isEqualTo(syncBlocks.get(0));
    assertThat(blocksWithReceipts.get(0).getReceipts()).isEqualTo(receiptsForBlock1);

    // block2 was deduped out of the request; combineBlocksAndReceipts finds its receipts via the
    // shared receipt root, so it gets the same receipts as block1
    assertThat(blocksWithReceipts.get(1).getBlock()).isEqualTo(syncBlocks.get(1));
    assertThat(blocksWithReceipts.get(1).getReceipts()).isEqualTo(receiptsForBlock1);

    // block3 should have its own receipts
    assertThat(blocksWithReceipts.get(2).getBlock()).isEqualTo(syncBlocks.get(2));
    assertThat(blocksWithReceipts.get(2).getReceipts()).isEqualTo(receiptsForBlock3);

    // Verify the task was executed only once (deduplication worked)
    verify(peerTaskExecutor, times(1)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldHandlePartialSuccessThenFailureThenSuccess()
      throws ExecutionException, InterruptedException {
    // Given: 4 blocks with transactions
    final List<Block> blocks = gen.blockSequence(5).subList(1, 5);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    final var allReceiptsPerBlock =
        blocks.stream()
            .map(gen::receipts)
            .map(rs -> receiptsToSyncReceipts(rs, ETH69_RECEIPT_CONFIGURATION))
            .toList();

    // First call returns partial success (first 2 blocks only)
    final var firstSuccessResult =
        new PeerTaskExecutorResult<>(
            Optional.of(
                Map.of(
                    syncBlocks.get(0), allReceiptsPerBlock.get(0),
                    syncBlocks.get(1), allReceiptsPerBlock.get(1))),
            SUCCESS,
            emptyList());

    // Second call for remaining blocks returns failure
    final var failureResult =
        new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
            Optional.empty(), TIMEOUT, emptyList());

    // Third call (retry) returns the remaining 2 blocks successfully
    final var secondSuccessResult =
        new PeerTaskExecutorResult<>(
            Optional.of(
                Map.of(
                    syncBlocks.get(2), allReceiptsPerBlock.get(2),
                    syncBlocks.get(3), allReceiptsPerBlock.get(3))),
            SUCCESS,
            emptyList());

    when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
        .thenReturn(firstSuccessResult) // Iteration 1: SUCCESS with 2 blocks
        .thenReturn(failureResult) // Iteration 2: FAILURE on remaining 2 blocks
        .thenReturn(secondSuccessResult); // Iteration 3: SUCCESS with remaining 2 blocks

    // When: downloading receipts
    final CompletableFuture<List<SyncBlockWithReceipts>> result =
        downloadSyncReceiptsStep.apply(syncBlocks);

    // Then: should return all 4 blocks with receipts
    final List<SyncBlockWithReceipts> blocksWithReceipts = result.get();
    assertThat(blocksWithReceipts).hasSize(4);

    // Verify all blocks have correct receipts
    for (int i = 0; i < blocksWithReceipts.size(); i++) {
      assertThat(blocksWithReceipts.get(i).getBlock()).isEqualTo(syncBlocks.get(i));
      assertThat(blocksWithReceipts.get(i).getReceipts()).isEqualTo(allReceiptsPerBlock.get(i));
    }

    // Verify the task was executed 3 times:
    // 1. Partial success (2 blocks)
    // 2. Failure on remaining blocks
    // 3. Success on retry for remaining blocks
    verify(peerTaskExecutor, times(3)).execute(any(GetSyncReceiptsFromPeerTask.class));
  }

  @Test
  public void shouldTimeoutAfterConfiguredDuration() throws Exception {
    // Given: 1 block with transactions and a very short timeout
    final List<Block> blocks = gen.blockSequence(2).subList(1, 2);
    final List<SyncBlock> syncBlocks = blocksToSyncBlocks(blocks);

    // Create a real EthScheduler (not deterministic) to test timeout behavior
    final EthScheduler realScheduler = new EthScheduler(1, 1, 1, new NoOpMetricsSystem());
    final EthContext realEthContext = mock(EthContext.class);
    when(realEthContext.getScheduler()).thenReturn(realScheduler);
    when(realEthContext.getPeerTaskExecutor()).thenReturn(peerTaskExecutor);

    try {
      // Create a new instance with a very short timeout for testing (100ms)
      final DownloadSyncReceiptsStep shortTimeoutStep =
          new DownloadSyncReceiptsStep(
              protocolSchedule,
              realEthContext,
              syncTransactionReceiptEncoder,
              Duration.ofMillis(100));

      // Mock continuous failures that would retry indefinitely without timeout
      final var failureResult =
          new PeerTaskExecutorResult<Map<SyncBlock, List<SyncTransactionReceipt>>>(
              Optional.empty(), TIMEOUT, emptyList());

      when(peerTaskExecutor.execute(any(GetSyncReceiptsFromPeerTask.class)))
          .thenReturn(failureResult);

      // When: downloading receipts with short timeout
      final CompletableFuture<List<SyncBlockWithReceipts>> result =
          shortTimeoutStep.apply(syncBlocks);

      // Then: should timeout quickly with TimeoutException (wrapped in ExecutionException)
      assertThatThrownBy(result::get)
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    } finally {
      // Clean up the real scheduler
      realScheduler.stop();
      realScheduler.awaitStop();
    }
  }
}
