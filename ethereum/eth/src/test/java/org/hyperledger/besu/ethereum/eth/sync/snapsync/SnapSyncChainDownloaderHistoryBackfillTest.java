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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MissingBlockBodies;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.common.BackwardHeaderDriver;
import org.hyperledger.besu.ethereum.eth.sync.common.ChainSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.common.SingleBlockHeaderDownloader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Where the forward download starts when the chain history is left for after the sync. */
@ExtendWith(MockitoExtension.class)
public class SnapSyncChainDownloaderHistoryBackfillTest {

  private static final long PIVOT = 20_000;
  private static final long LAST_BLOCK_LEFT =
      PIVOT - SnapSyncChainDownloader.BLOCKS_BEFORE_PIVOT_WITH_BODIES;
  private static final Difficulty GENESIS_TOTAL_DIFFICULTY = Difficulty.of(17);

  @Mock private SnapSyncChainDownloadPipelineFactory pipelineFactory;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private ProtocolContext protocolContext;
  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private SyncState syncState;
  @Mock private SyncDurationMetrics syncDurationMetrics;
  @Mock private MutableBlockchain blockchain;
  @Mock private EthScheduler scheduler;
  @Mock private SingleBlockHeaderDownloader headerDownloader;

  @TempDir private Path tempDir;

  private final BlockHeader genesis = new BlockHeaderTestFixture().number(0).buildHeader();
  private final BlockHeader lastBlockLeft =
      new BlockHeaderTestFixture().number(LAST_BLOCK_LEFT).buildHeader();

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    lenient().when(protocolContext.getBlockchain()).thenReturn(blockchain);
    lenient().when(ethContext.getScheduler()).thenReturn(scheduler);
    lenient().when(ethContext.getEthPeers()).thenReturn(ethPeers);
    lenient().when(syncState.getCheckpoint()).thenReturn(Optional.empty());
    lenient().when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    lenient()
        .when(protocolSpec.getBlockHeaderFunctions())
        .thenReturn(new MainnetBlockHeaderFunctions());

    lenient().when(blockchain.getGenesisBlockHeader()).thenReturn(genesis);
    lenient().when(blockchain.getBlockHeader(0)).thenReturn(Optional.of(genesis));
    lenient()
        .when(blockchain.getTotalDifficultyByHash(genesis.getHash()))
        .thenReturn(Optional.of(GENESIS_TOTAL_DIFFICULTY));
    lenient()
        .when(blockchain.getBlockHeader(LAST_BLOCK_LEFT))
        .thenReturn(Optional.of(lastBlockLeft));
    firstBlockAfterGenesisHasDifficulty(Difficulty.ZERO);
    chainHeadIs(genesis);

    final Pipeline<Long> backwardPipeline = mock(Pipeline.class);
    final BackwardHeaderDriver driver = mock(BackwardHeaderDriver.class);
    lenient().when(driver.getMatchedAncestor()).thenReturn(Optional.empty());
    lenient()
        .when(pipelineFactory.createBackwardHeaderDownloadPipeline(any()))
        .thenReturn(
            new SnapSyncChainDownloadPipelineFactory.BackwardHeaderPipelineResult(
                backwardPipeline, driver));
    final Pipeline<List<BlockHeader>> forwardPipeline = mock(Pipeline.class);
    lenient()
        .when(
            pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(anyLong(), any(), any()))
        .thenReturn(forwardPipeline);
    lenient()
        .when(scheduler.startPipeline(any()))
        .thenReturn(CompletableFuture.completedFuture(null));
    lenient().when(ethPeers.peerCount()).thenReturn(1);
    lenient().when(pipelineFactory.isHistoryBackfillEnabled()).thenReturn(true);
  }

  @Test
  public void shouldStartWithTheNewestBlocksAndRecordTheRestAsMissing() throws Exception {
    downloadChain();

    verify(pipelineFactory)
        .createForwardBodiesAndReceiptsDownloadPipeline(eq(LAST_BLOCK_LEFT), any(), any());
    verify(blockchain)
        .unsafeSetMissingBlockBodies(Optional.of(new MissingBlockBodies(1, LAST_BLOCK_LEFT)));
    // the chain head moves on from the last block left, which takes its total difficulty
    verify(blockchain).unsafeStoreTotalDifficulty(lastBlockLeft, GENESIS_TOTAL_DIFFICULTY);
  }

  @Test
  public void shouldDownloadEveryBlockWhenTheHistoryIsNotLeftForLater() throws Exception {
    when(pipelineFactory.isHistoryBackfillEnabled()).thenReturn(false);

    downloadChain();

    verify(pipelineFactory).createForwardBodiesAndReceiptsDownloadPipeline(eq(0L), any(), any());
    verify(blockchain, never()).unsafeSetMissingBlockBodies(any());
  }

  @Test
  public void shouldDownloadEveryBlockOfAChainThatStartsBeforeTheMerge() throws Exception {
    // its blocks do not share the total difficulty of the block the download starts after
    firstBlockAfterGenesisHasDifficulty(Difficulty.of(1000));

    downloadChain();

    verify(pipelineFactory).createForwardBodiesAndReceiptsDownloadPipeline(eq(0L), any(), any());
    verify(blockchain, never()).unsafeSetMissingBlockBodies(any());
  }

  @Test
  public void shouldDownloadEveryBlockOfAShortChain() throws Exception {
    final BlockHeader pivot =
        new BlockHeaderTestFixture()
            .number(SnapSyncChainDownloader.BLOCKS_BEFORE_PIVOT_WITH_BODIES)
            .buildHeader();

    downloadChainTo(pivot);

    verify(pipelineFactory).createForwardBodiesAndReceiptsDownloadPipeline(eq(0L), any(), any());
    verify(blockchain, never()).unsafeSetMissingBlockBodies(any());
  }

  @Test
  public void shouldContinueFromTheChainHeadOnceBlocksAreLeftForLater() throws Exception {
    // a sync that was interrupted after it started with the newest blocks
    final BlockHeader chainHead =
        new BlockHeaderTestFixture().number(LAST_BLOCK_LEFT + 100).buildHeader();
    chainHeadIs(chainHead);
    when(blockchain.getMissingBlockBodies())
        .thenReturn(Optional.of(new MissingBlockBodies(1, LAST_BLOCK_LEFT)));

    // with a pivot far enough ahead to leave blocks out again
    downloadChainTo(new BlockHeaderTestFixture().number(3 * PIVOT).buildHeader());

    verify(pipelineFactory)
        .createForwardBodiesAndReceiptsDownloadPipeline(eq(LAST_BLOCK_LEFT + 100), any(), any());
    verify(blockchain, never()).unsafeSetMissingBlockBodies(any());
  }

  @Test
  public void shouldLeaveBlocksOutAgainWhenTheChainHeadNeverMovedPastThem() throws Exception {
    // a sync that was interrupted before the first of the newest blocks was stored
    when(blockchain.getMissingBlockBodies())
        .thenReturn(Optional.of(new MissingBlockBodies(1, LAST_BLOCK_LEFT - 500)));

    downloadChain();

    verify(pipelineFactory)
        .createForwardBodiesAndReceiptsDownloadPipeline(eq(LAST_BLOCK_LEFT), any(), any());
    verify(blockchain)
        .unsafeSetMissingBlockBodies(Optional.of(new MissingBlockBodies(1, LAST_BLOCK_LEFT)));
  }

  private void downloadChain() throws Exception {
    downloadChainTo(new BlockHeaderTestFixture().number(PIVOT).buildHeader());
  }

  private void downloadChainTo(final BlockHeader pivot) throws Exception {
    final SnapSyncChainDownloader downloader =
        new SnapSyncChainDownloader(
            pipelineFactory,
            SynchronizerConfiguration.builder().build(),
            protocolSchedule,
            protocolContext,
            ethContext,
            syncState,
            syncDurationMetrics,
            pivot,
            new ChainSyncStateStorage(tempDir),
            headerDownloader);
    downloader.onWorldStateHealFinished();
    downloader.start().get(5, TimeUnit.SECONDS);
  }

  /** The chain head, which is on the canonical chain and has its body. */
  private void chainHeadIs(final BlockHeader chainHead) {
    lenient().when(blockchain.getChainHeadHeader()).thenReturn(chainHead);
    lenient().when(blockchain.getChainHeadBlockNumber()).thenReturn(chainHead.getNumber());
    lenient().when(blockchain.blockIsOnCanonicalChain(chainHead.getHash())).thenReturn(true);
    lenient()
        .when(blockchain.getBlockBody(chainHead.getHash()))
        .thenReturn(Optional.of(mock(BlockBody.class)));
    lenient()
        .when(blockchain.getBlockHeader(chainHead.getNumber()))
        .thenReturn(Optional.of(chainHead));
  }

  private void firstBlockAfterGenesisHasDifficulty(final Difficulty difficulty) {
    lenient()
        .when(blockchain.getBlockHeader(1))
        .thenReturn(
            Optional.of(
                new BlockHeaderTestFixture().number(1).difficulty(difficulty).buildHeader()));
  }
}
