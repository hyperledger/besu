///*
// * Copyright contributors to Hyperledger Besu.
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// * in compliance with the License. You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software distributed under the License
// * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// * or implied. See the License for the specific language governing permissions and limitations under
// * the License.
// *
// * SPDX-License-Identifier: Apache-2.0
// */
//package org.hyperledger.besu.ethereum.eth.sync.fastsync;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//import org.hyperledger.besu.datatypes.Hash;
//import org.hyperledger.besu.ethereum.core.BlockHeader;
//import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
//import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
//import org.hyperledger.besu.metrics.SyncDurationMetrics;
//import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
//import org.hyperledger.besu.services.pipeline.Pipeline;
//
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutionException;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.ArgumentCaptor;
//
///**
// * Unit tests for TwoStageFastSyncChainDownloader.
// *
// * <p>Tests the orchestration of two sequential pipelines: backward header download followed by
// * forward bodies/receipts download.
// */
//class TwoStageFastSyncChainDownloaderTest {
//
//  private FastSyncDownloadPipelineFactory pipelineFactory;
//  private EthScheduler scheduler;
//  private SyncState syncState;
//  private TwoStageFastSyncChainDownloader downloader;
//
//  private static final Hash PIVOT_BLOCK_HASH = Hash.fromHexString("0x1234");
//
//  @SuppressWarnings("unchecked")
//  @BeforeEach
//  void setUp() {
//    pipelineFactory = mock(FastSyncDownloadPipelineFactory.class);
//    scheduler = mock(EthScheduler.class);
//    syncState = mock(SyncState.class);
//
//    downloader =
//        new TwoStageFastSyncChainDownloader(
//            pipelineFactory,
//            scheduler,
//            syncState,
//            PIVOT_BLOCK_HASH,
//            new NoOpMetricsSystem(),
//            SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldExecuteStage1ThenStage2() {
//    // Given: mocked pipelines that complete successfully
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//
//    when(scheduler.startPipeline(headerPipeline))
//        .thenReturn(CompletableFuture.completedFuture(null));
//    when(scheduler.startPipeline(bodiesPipeline))
//        .thenReturn(CompletableFuture.completedFuture(null));
//
//    // When: start() called
//    final CompletableFuture<Void> result = downloader.start();
//
//    // Then: stage 1 completes, then stage 2 starts and completes
//    assertThat(result).isCompleted();
//    verify(pipelineFactory).createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH);
//    verify(pipelineFactory).createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, );
//    verify(scheduler, times(2)).startPipeline(any());
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldNotStartStage2IfStage1Fails() {
//    // Given: stage 1 pipeline that fails
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//
//    final CompletableFuture<Void> failedFuture = CompletableFuture.failedFuture(new RuntimeException("Stage 1 failed"));
//    when(scheduler.startPipeline(headerPipeline)).thenReturn(failedFuture);
//
//    // When: start() called
//    final CompletableFuture<Void> result = downloader.start();
//
//    // Then: stage 2 never starts (result completes with error handled)
//    assertThat(result).isCompletedWithValue(null);
//    verify(pipelineFactory).createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH);
//    verify(pipelineFactory, never()).createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, );
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldHandleCancellationDuringStage1() {
//    // Given: downloader with slow stage 1
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//
//    final CompletableFuture<Void> slowFuture = new CompletableFuture<>();
//    when(scheduler.startPipeline(headerPipeline)).thenReturn(slowFuture);
//
//    // When: cancel() called during stage 1
//    downloader.start();
//    downloader.cancel();
//
//    // Then: stage 1 pipeline aborted
//    verify(headerPipeline).abort();
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldHandleCancellationDuringStage2() {
//    // Given: downloader running stage 2
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//
//    when(scheduler.startPipeline(headerPipeline))
//        .thenReturn(CompletableFuture.completedFuture(null));
//
//    final CompletableFuture<Void> slowFuture = new CompletableFuture<>();
//    when(scheduler.startPipeline(bodiesPipeline)).thenReturn(slowFuture);
//
//    // When: cancel() called during stage 2
//    downloader.start();
//    downloader.cancel();
//
//    // Then: stage 2 pipeline aborted
//    verify(bodiesPipeline).abort();
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldCreateBothPipelinesWithCorrectParams() {
//    // Given: mocked pipeline factory
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//    when(scheduler.startPipeline(any())).thenReturn(CompletableFuture.completedFuture(null));
//
//    // When: start() called
//    downloader.start();
//
//    // Then: both pipelines created with correct pivotBlockNumber
//    verify(pipelineFactory).createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH);
//    verify(pipelineFactory).createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, );
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldCreateForwardBodiesPipelineWithCorrectParams() {
//    // Given: mocked pipeline factory
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//
//    when(scheduler.startPipeline(any())).thenReturn(CompletableFuture.completedFuture(null));
//
//    // When: start() called
//    downloader.start();
//
//    // Then: createForwardBodiesAndReceiptsDownloadPipeline(pivotBlockNumber) called
//    verify(pipelineFactory).createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, );
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldStartPipelinesSequentially() {
//    // Given: mocked pipelines
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//
//    when(scheduler.startPipeline(headerPipeline))
//        .thenReturn(CompletableFuture.completedFuture(null));
//    when(scheduler.startPipeline(bodiesPipeline))
//        .thenReturn(CompletableFuture.completedFuture(null));
//
//    // When: start() called
//    downloader.start();
//
//    // Then: pipelines started in correct order
//    final ArgumentCaptor<Pipeline<?>> pipelineCaptor = ArgumentCaptor.forClass(Pipeline.class);
//    verify(scheduler, times(2)).startPipeline(pipelineCaptor.capture());
//
//    final List<Pipeline<?>> pipelines = pipelineCaptor.getAllValues();
//    assertThat(pipelines.get(0)).isSameAs(headerPipeline);
//    assertThat(pipelines.get(1)).isSameAs(bodiesPipeline);
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldCompleteSuccessfullyWhenBothStagesComplete() {
//    // Given: both stages complete successfully
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//
//    when(scheduler.startPipeline(any())).thenReturn(CompletableFuture.completedFuture(null));
//
//    // When: start() called
//    final CompletableFuture<Void> result = downloader.start();
//
//    // Then: result completes successfully
//    assertThat(result).isCompletedWithValue(null);
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldHandleStage2Exception() {
//    // Given: stage 2 pipeline throws exception
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//    final Pipeline<List<BlockHeader>> bodiesPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//    when(pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(PIVOT_BLOCK_HASH, ))
//        .thenReturn(bodiesPipeline);
//
//    when(scheduler.startPipeline(headerPipeline))
//        .thenReturn(CompletableFuture.completedFuture(null));
//
//    final CompletableFuture<Void> failedFuture = new CompletableFuture<>();
//    failedFuture.completeExceptionally(new RuntimeException("Stage 2 failed"));
//    when(scheduler.startPipeline(bodiesPipeline)).thenReturn(failedFuture);
//
//    // When: start() called
//    final CompletableFuture<Void> result = downloader.start();
//
//    // Then: exception handled, future completes
//    assertThat(result).isCompleted();
//  }
//
//  @Test
//  void shouldNotAbortPipelineWhenNotStarted() {
//    // Given: downloader not started
//
//    // When: cancel() called before start()
//    downloader.cancel();
//
//    // Then: no exception thrown (currentPipeline is null, safely handled)
//    // This test verifies cancel() can be called before start() without errors
//  }
//
//  @SuppressWarnings("unchecked")
//  @Test
//  void shouldCompleteFutureEvenWhenStage1CompletesExceptionally()
//      throws ExecutionException, InterruptedException {
//    // Given: stage 1 fails
//    final Pipeline<Long> headerPipeline = mock(Pipeline.class);
//
//    when(pipelineFactory.createBackwardHeaderDownloadPipeline(PIVOT_BLOCK_HASH))
//        .thenReturn(headerPipeline);
//
//    final CompletableFuture<Void> failedFuture = new CompletableFuture<>();
//    failedFuture.completeExceptionally(new RuntimeException("Failed"));
//    when(scheduler.startPipeline(headerPipeline)).thenReturn(failedFuture);
//
//    // When: start() called
//    final CompletableFuture<Void> result = downloader.start();
//
//    // Then: future completes (not exceptionally, error is handled)
//    assertThat(result).isCompleted();
//    assertThat(result.get()).isNull(); // handle() returns null
//  }
//}
