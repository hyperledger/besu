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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MissingBlockBodies;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.common.BackwardHeaderDriver;
import org.hyperledger.besu.ethereum.eth.sync.common.ChainSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.common.ImportSyncBlocksStep;
import org.hyperledger.besu.ethereum.eth.sync.common.SingleBlockHeaderDownloader;
import org.hyperledger.besu.ethereum.eth.sync.common.StoreMissingBodiesStep;
import org.hyperledger.besu.ethereum.eth.sync.common.checkpoint.ImmutableCheckpoint;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The chain history left for after the sync on a chain like mainnet in small: proof of work up to
 * the merge, and a sync that starts from a checkpoint at the first proof of stake block instead of
 * from genesis. The blockchain, the chain downloader and the steps that store blocks are the real
 * ones. Only what talks to peers is replaced.
 */
public class SnapSyncHistoryBackfillFromCheckpointTest {

  private static final int CHECKPOINT = 50;
  private static final int BLOCKS_WITH_BODIES =
      (int) SnapSyncChainDownloader.BLOCKS_BEFORE_PIVOT_WITH_BODIES;
  private static final int PIVOT = CHECKPOINT + BLOCKS_WITH_BODIES + 300;
  private static final int LAST_BLOCK_LEFT = PIVOT - BLOCKS_WITH_BODIES;
  private static final Difficulty PROOF_OF_WORK_DIFFICULTY = Difficulty.of(1000);
  // genesis and the 49 blocks after it
  private static final Difficulty TERMINAL_TOTAL_DIFFICULTY = Difficulty.of(1000L * CHECKPOINT);

  // the index of a block is its number
  private static final List<Block> CHAIN = chain();

  @TempDir private Path tempDir;

  @ParameterizedTest(name = "headers down to the checkpoint only: {0}")
  @ValueSource(booleans = {false, true})
  public void shouldLeaveTheBlocksAfterTheCheckpointForLater(final boolean headersToCheckpointOnly)
      throws Exception {
    final Sync sync = new Sync(true, headersToCheckpointOnly);

    sync.downloadChain();

    assertThat(sync.forwardAnchor.get()).isEqualTo(LAST_BLOCK_LEFT);
    final MissingBlockBodies missing = new MissingBlockBodies(CHECKPOINT + 1, LAST_BLOCK_LEFT);
    assertThat(sync.blockchain.getMissingBlockBodies()).contains(missing);
    // the sync is complete with the pivot block as the chain head
    assertThat(sync.blockchain.getChainHeadHeader()).isEqualTo(header(PIVOT));
    assertThat(sync.blockchain.getChainHead().getTotalDifficulty())
        .isEqualTo(TERMINAL_TOTAL_DIFFICULTY);
    assertThat(sync.blockchain.getBlockBody(header(LAST_BLOCK_LEFT + 1).getHash())).isPresent();
    assertThat(sync.blockchain.getBlockBody(header(LAST_BLOCK_LEFT).getHash())).isEmpty();
    assertThat(sync.blockchain.getEarliestBlockNumber()).contains(LAST_BLOCK_LEFT + 1L);

    // the download of the history starts from the checkpoint with its total difficulty
    sync.startMissingBodiesDownloader();
    verify(sync.pipelineFactory)
        .createMissingBodiesDownloadPipeline(missing, TERMINAL_TOTAL_DIFFICULTY);
  }

  @ParameterizedTest(name = "headers down to the checkpoint only: {0}")
  @ValueSource(booleans = {false, true})
  public void shouldEndUpWithTheChainOfASyncThatDownloadsTheHistoryFirst(
      final boolean headersToCheckpointOnly) throws Exception {
    final Sync historyFirst = new Sync(false, headersToCheckpointOnly);
    historyFirst.downloadChain();
    assertThat(historyFirst.forwardAnchor.get()).isEqualTo(CHECKPOINT);
    verify(historyFirst.pipelineFactory, never()).createMissingBodiesDownloadPipeline(any(), any());

    final Sync historyLater = new Sync(true, headersToCheckpointOnly);
    historyLater.downloadChain();
    historyLater.downloadHistory();

    assertThat(historyLater.blockchain.getMissingBlockBodies()).isEmpty();
    assertThat(historyLater.blockchain.getChainHeadHeader())
        .isEqualTo(historyFirst.blockchain.getChainHeadHeader());
    assertThat(historyLater.blockchain.getChainHead().getTotalDifficulty())
        .isEqualTo(historyFirst.blockchain.getChainHead().getTotalDifficulty());
    assertThat(historyLater.blockchain.getEarliestBlockNumber())
        .isEqualTo(historyFirst.blockchain.getEarliestBlockNumber())
        .contains(CHECKPOINT + 1L);
    for (int number = 1; number <= PIVOT; number++) {
      final BlockHeader header = header(number);
      assertThat(historyLater.blockchain.getBlockHashByNumber(number))
          .as("hash of block %s", number)
          .isEqualTo(historyFirst.blockchain.getBlockHashByNumber(number));
      assertThat(historyLater.blockchain.getBlockBody(header.getHash()).isPresent())
          .as("body of block %s", number)
          .isEqualTo(historyFirst.blockchain.getBlockBody(header.getHash()).isPresent())
          .isEqualTo(number > CHECKPOINT);
      assertThat(historyLater.blockchain.getTotalDifficultyByHash(header.getHash()))
          .as("total difficulty of block %s", number)
          .isEqualTo(historyFirst.blockchain.getTotalDifficultyByHash(header.getHash()));
    }
  }

  @Test
  public void shouldHaveBuiltAChainThatMergesAtTheCheckpoint() {
    assertThat(header(CHECKPOINT - 1).getDifficulty()).isEqualTo(PROOF_OF_WORK_DIFFICULTY);
    assertThat(header(CHECKPOINT).getDifficulty()).isEqualTo(Difficulty.ZERO);
    assertThat(header(PIVOT).getParentHash()).isEqualTo(header(PIVOT - 1).getHash());
  }

  /** One sync of the chain into an empty blockchain. */
  private class Sync {
    private final MutableBlockchain blockchain = createInMemoryBlockchain(CHAIN.getFirst());
    private final SnapSyncChainDownloadPipelineFactory pipelineFactory =
        mock(SnapSyncChainDownloadPipelineFactory.class);
    private final ProtocolContext protocolContext = mock(ProtocolContext.class);
    private final EthContext ethContext = mock(EthContext.class);
    private final EthScheduler scheduler = mock(EthScheduler.class);
    private final SyncState syncState = mock(SyncState.class);
    private final AtomicLong forwardAnchor = new AtomicLong(-1);
    private final boolean headersToCheckpointOnly;

    @SuppressWarnings("unchecked")
    private final Pipeline<Long> backwardPipeline = mock(Pipeline.class);

    @SuppressWarnings("unchecked")
    private final Pipeline<List<BlockHeader>> forwardPipeline = mock(Pipeline.class);

    @SuppressWarnings("unchecked")
    private final Pipeline<List<BlockHeader>> missingBodiesPipeline = mock(Pipeline.class);

    Sync(final boolean historyBackfillEnabled, final boolean headersToCheckpointOnly) {
      this.headersToCheckpointOnly = headersToCheckpointOnly;
      final EthPeers ethPeers = mock(EthPeers.class);
      when(ethPeers.peerCount()).thenReturn(1);
      when(ethContext.getEthPeers()).thenReturn(ethPeers);
      when(ethContext.getScheduler()).thenReturn(scheduler);
      when(protocolContext.getBlockchain()).thenReturn(blockchain);
      when(syncState.getCheckpoint())
          .thenReturn(
              Optional.of(
                  ImmutableCheckpoint.builder()
                      .blockNumber(CHECKPOINT)
                      .blockHash(header(CHECKPOINT).getHash())
                      .totalDifficulty(TERMINAL_TOTAL_DIFFICULTY)
                      .build()));

      when(pipelineFactory.isHistoryBackfillEnabled()).thenReturn(historyBackfillEnabled);
      final BackwardHeaderDriver driver = mock(BackwardHeaderDriver.class);
      when(driver.getMatchedAncestor()).thenReturn(Optional.empty());
      when(pipelineFactory.createBackwardHeaderDownloadPipeline(any()))
          .thenReturn(
              new SnapSyncChainDownloadPipelineFactory.BackwardHeaderPipelineResult(
                  backwardPipeline, driver));
      when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(anyLong(), any(), any()))
          .thenAnswer(
              invocation -> {
                forwardAnchor.set(invocation.getArgument(0));
                return forwardPipeline;
              });
      when(pipelineFactory.createMissingBodiesDownloadPipeline(any(), any()))
          .thenReturn(missingBodiesPipeline);
      when(scheduler.startPipeline(any())).thenAnswer(invocation -> run(invocation.getArgument(0)));
    }

    /** What the pipelines do with the blockchain, without the peers. */
    private CompletableFuture<Void> run(final Pipeline<?> pipeline) {
      if (pipeline == backwardPipeline) {
        // the header download stores the headers from the pivot block down to its anchor
        blockchain.storeBlockHeaders(
            headers(headersToCheckpointOnly ? CHECKPOINT + 1 : 1, PIVOT).reversed());
        return CompletableFuture.completedFuture(null);
      }
      if (pipeline == forwardPipeline) {
        final int anchor = (int) forwardAnchor.get();
        final ImportSyncBlocksStep importStep =
            new ImportSyncBlocksStep(
                protocolContext, null, syncState, anchor, PIVOT, false, Optional.empty());
        // the batches arrive as their downloads complete
        final int middle = anchor + (PIVOT - anchor) / 2;
        importStep.accept(batch(middle + 1, PIVOT));
        importStep.accept(batch(anchor + 1, middle));
        return CompletableFuture.completedFuture(null);
      }
      // the download of the history, which downloadHistory() stands in for
      return new CompletableFuture<>();
    }

    void downloadChain() throws Exception {
      final BlockHeader checkpoint = header(CHECKPOINT);
      final SingleBlockHeaderDownloader headerDownloader = mock(SingleBlockHeaderDownloader.class);
      when(headerDownloader.downloadBlockHeader(checkpoint.getHash()))
          .thenReturn(CompletableFuture.completedFuture(checkpoint));
      final SnapSyncChainDownloader downloader =
          new SnapSyncChainDownloader(
              pipelineFactory,
              SynchronizerConfiguration.builder()
                  .snapSyncHeadersToCheckpointOnly(headersToCheckpointOnly)
                  .build(),
              mock(ProtocolSchedule.class),
              protocolContext,
              ethContext,
              syncState,
              mock(SyncDurationMetrics.class),
              header(PIVOT),
              new ChainSyncStateStorage(Files.createTempDirectory(tempDir, "sync")),
              headerDownloader);
      downloader.onWorldStateHealFinished();
      downloader.start().get(30, TimeUnit.SECONDS);
    }

    void startMissingBodiesDownloader() {
      new MissingBodiesDownloader(pipelineFactory, blockchain, ethContext).start();
    }

    /** Stores the bodies and receipts that were left for later, as their download would. */
    void downloadHistory() {
      final MissingBlockBodies missing = blockchain.getMissingBlockBodies().orElseThrow();
      final Difficulty totalDifficulty =
          blockchain
              .getBlockHeader(missing.firstBlock() - 1)
              .flatMap(header -> blockchain.getTotalDifficultyByHash(header.getHash()))
              .orElseThrow();
      final StoreMissingBodiesStep storeStep =
          new StoreMissingBodiesStep(
              blockchain, missing, totalDifficulty, false, Clock.systemUTC());
      final int first = (int) missing.firstBlock();
      final int last = (int) missing.lastBlock();
      final int middle = first + (last - first) / 2;
      storeStep.accept(batch(middle + 1, last));
      storeStep.accept(batch(first, middle));
    }
  }

  private static List<Block> chain() {
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<Block> chain = new ArrayList<>(PIVOT + 1);
    chain.add(gen.genesisBlock(emptyBlock().setDifficulty(PROOF_OF_WORK_DIFFICULTY)));
    for (int number = 1; number <= PIVOT; number++) {
      chain.add(
          gen.block(
              emptyBlock()
                  .setBlockNumber(number)
                  .setParentHash(chain.get(number - 1).getHash())
                  .setDifficulty(
                      number < CHECKPOINT ? PROOF_OF_WORK_DIFFICULTY : Difficulty.ZERO)));
    }
    return chain;
  }

  private static BlockOptions emptyBlock() {
    return BlockOptions.create().hasTransactions(false).hasOmmers(false);
  }

  private static BlockHeader header(final int number) {
    return CHAIN.get(number).getHeader();
  }

  private static List<BlockHeader> headers(final int firstBlock, final int lastBlock) {
    return CHAIN.subList(firstBlock, lastBlock + 1).stream().map(Block::getHeader).toList();
  }

  /** The blocks with the given numbers, as a batch of the download. */
  private static List<SyncBlockWithReceipts> batch(final int firstBlock, final int lastBlock) {
    return CHAIN.subList(firstBlock, lastBlock + 1).stream()
        .map(
            block ->
                new SyncBlockWithReceipts(
                    new SyncBlock(
                        block.getHeader(),
                        SyncBlockBody.emptyWithNullWithdrawals(
                            ProtocolScheduleFixture.TESTING_NETWORK)),
                    Collections.emptyList()))
        .toList();
  }
}
