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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.MissingBlockBodies;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MissingBodiesDownloaderTest {

  private static final MissingBlockBodies MISSING = new MissingBlockBodies(101, 900);
  private static final Difficulty TOTAL_DIFFICULTY = Difficulty.of(42);

  @Mock private SnapSyncChainDownloadPipelineFactory pipelineFactory;
  @Mock private MutableBlockchain blockchain;
  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private EthScheduler scheduler;
  @Mock private Pipeline<List<BlockHeader>> pipeline;

  private final CompletableFuture<Void> pipelineResult = new CompletableFuture<>();
  private MissingBodiesDownloader downloader;

  @BeforeEach
  public void setUp() {
    lenient().when(ethContext.getScheduler()).thenReturn(scheduler);
    lenient().when(ethContext.getEthPeers()).thenReturn(ethPeers);
    lenient().when(ethPeers.peerCount()).thenReturn(3);
    lenient().when(scheduler.startPipeline(any())).thenReturn(pipelineResult);
    lenient()
        .when(pipelineFactory.createMissingBodiesDownloadPipeline(any(), any()))
        .thenReturn(pipeline);

    // the block before the first missing one, which the missing ones share the difficulty of
    final BlockHeader lastBlockBefore = new BlockHeaderTestFixture().number(100).buildHeader();
    lenient().when(blockchain.getBlockHeader(100)).thenReturn(Optional.of(lastBlockBefore));
    lenient()
        .when(blockchain.getTotalDifficultyByHash(lastBlockBefore.getHash()))
        .thenReturn(Optional.of(TOTAL_DIFFICULTY));
    lenient().when(blockchain.getMissingBlockBodies()).thenReturn(Optional.of(MISSING));

    downloader = new MissingBodiesDownloader(pipelineFactory, blockchain, ethContext);
  }

  @Test
  public void shouldDownloadTheBlocksRecordedAsMissing() {
    downloader.start();

    verify(pipelineFactory).createMissingBodiesDownloadPipeline(MISSING, TOTAL_DIFFICULTY);
    verify(scheduler).startPipeline(pipeline);
  }

  @Test
  public void shouldDoNothingWhenNoBlockIsMissing() {
    when(blockchain.getMissingBlockBodies()).thenReturn(Optional.empty());

    downloader.start();

    verify(scheduler, never()).startPipeline(any());
  }

  @Test
  public void shouldNotStartASecondDownload() {
    downloader.start();
    downloader.start();

    verify(scheduler, times(1)).startPipeline(any());
  }

  @Test
  public void shouldWaitForPeers() {
    when(ethPeers.peerCount()).thenReturn(0);

    downloader.start();

    verify(scheduler, never()).startPipeline(any());
    final Runnable retry = scheduledRetry();

    when(ethPeers.peerCount()).thenReturn(1);
    retry.run();

    verify(scheduler).startPipeline(pipeline);
  }

  @Test
  public void shouldContinueWithWhatIsStillMissingAfterAFailure() {
    downloader.start();
    final MissingBlockBodies stillMissing = new MissingBlockBodies(501, 900);
    final BlockHeader lastBlockStored = new BlockHeaderTestFixture().number(500).buildHeader();
    when(blockchain.getMissingBlockBodies()).thenReturn(Optional.of(stillMissing));
    when(blockchain.getBlockHeader(500)).thenReturn(Optional.of(lastBlockStored));
    when(blockchain.getTotalDifficultyByHash(lastBlockStored.getHash()))
        .thenReturn(Optional.of(TOTAL_DIFFICULTY));

    pipelineResult.completeExceptionally(new RuntimeException("no peer answered"));
    scheduledRetry().run();

    verify(pipelineFactory).createMissingBodiesDownloadPipeline(stillMissing, TOTAL_DIFFICULTY);
  }

  @Test
  public void shouldBeDoneWhenThePipelineCompletesAndNothingIsMissing() {
    downloader.start();
    when(blockchain.getMissingBlockBodies()).thenReturn(Optional.empty());

    pipelineResult.complete(null);

    verify(scheduler, times(1)).startPipeline(any());
    verify(scheduler, never()).scheduleFutureTask(any(Runnable.class), any(Duration.class));
  }

  @Test
  public void shouldAbortTheDownloadWhenStopped() {
    downloader.start();

    downloader.stop();

    verify(pipeline).abort();
    // the aborted pipeline fails, which must not start it again
    pipelineResult.completeExceptionally(new RuntimeException("aborted"));
    scheduledRetry().run();
    verify(scheduler, times(1)).startPipeline(any());
  }

  private Runnable scheduledRetry() {
    final ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).scheduleFutureTask(retry.capture(), any(Duration.class));
    return retry.getValue();
  }
}
