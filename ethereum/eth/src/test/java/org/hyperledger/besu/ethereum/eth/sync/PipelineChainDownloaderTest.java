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
package org.hyperledger.besu.ethereum.eth.sync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PipelineChainDownloaderTest {

  @Mock private AbstractSyncTargetManager syncTargetManager;
  @Mock private DownloadPipelineFactory downloadPipelineFactory;
  @Mock private EthScheduler scheduler;
  @Mock private Pipeline<?> downloadPipeline;
  @Mock private Pipeline<Object> downloadPipeline2;
  @Mock private EthPeer peer1;
  @Mock private EthPeer peer2;
  @Mock private SyncState syncState;
  private final BlockHeader commonAncestor = new BlockHeaderTestFixture().buildHeader();
  private SyncTarget syncTarget;
  private SyncTarget syncTarget2;
  private PipelineChainDownloader chainDownloader;

  @BeforeEach
  public void setUp() {
    syncTarget = new SyncTarget(peer1, commonAncestor);
    syncTarget2 = new SyncTarget(peer2, commonAncestor);
    final NoOpMetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();
    chainDownloader =
        new PipelineChainDownloader(
            syncState,
            syncTargetManager,
            downloadPipelineFactory,
            scheduler,
            noOpMetricsSystem,
            SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
  }

  @Test
  public void shouldSelectSyncTargetWhenStarted() {
    when(syncTargetManager.findSyncTarget()).thenReturn(new CompletableFuture<>());
    chainDownloader.start();

    verify(syncTargetManager).findSyncTarget();
  }

  @Test
  public void shouldStartChainDownloadWhenTargetSelected() {
    final CompletableFuture<SyncTarget> selectTargetFuture = new CompletableFuture<>();
    when(syncTargetManager.findSyncTarget()).thenReturn(selectTargetFuture);
    when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);
    expectPipelineCreation(syncTarget, downloadPipeline, new CompletableFuture<>());
    chainDownloader.start();
    verifyNoInteractions(downloadPipelineFactory);

    selectTargetFuture.complete(syncTarget);

    verify(downloadPipelineFactory).createDownloadPipelineForSyncTarget(syncTarget);
    verify(downloadPipelineFactory)
        .startPipeline(scheduler, syncState, syncTarget, downloadPipeline);
  }

  @Test
  public void shouldUpdateSyncStateWhenTargetSelected() {
    final CompletableFuture<SyncTarget> selectTargetFuture = new CompletableFuture<>();
    when(syncTargetManager.findSyncTarget()).thenReturn(selectTargetFuture);
    when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);
    expectPipelineCreation(syncTarget, downloadPipeline, new CompletableFuture<>());
    chainDownloader.start();
    verifyNoInteractions(downloadPipelineFactory);
    selectTargetFuture.complete(syncTarget);

    verify(syncState).setSyncTarget(peer1, commonAncestor);
  }

  @Test
  public void shouldRetryWhenSyncTargetSelectionFailsAndSyncTargetManagerShouldContinue() {
    immediatelyCompletePauseAfterError();
    final CompletableFuture<SyncTarget> selectTargetFuture = new CompletableFuture<>();
    when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);
    when(syncTargetManager.findSyncTarget())
        .thenReturn(selectTargetFuture)
        .thenReturn(new CompletableFuture<>());
    chainDownloader.start();

    verify(syncTargetManager).findSyncTarget();

    selectTargetFuture.completeExceptionally(new RuntimeException("Nope"));

    verify(syncTargetManager, times(2)).findSyncTarget();
  }

  @Test
  public void shouldBeCompleteWhenSyncTargetSelectionFailsAndSyncTargetManagerShouldNotContinue() {
    final CompletableFuture<SyncTarget> selectTargetFuture = new CompletableFuture<>();
    when(syncTargetManager.shouldContinueDownloading()).thenReturn(false);
    when(syncTargetManager.findSyncTarget())
        .thenReturn(selectTargetFuture)
        .thenReturn(new CompletableFuture<>());
    final CompletableFuture<Void> result = chainDownloader.start();

    verify(syncTargetManager).findSyncTarget();

    final RuntimeException exception = new RuntimeException("Nope");
    selectTargetFuture.completeExceptionally(exception);

    assertExceptionallyCompletedWith(result, exception);
  }

  @Test
  public void shouldBeCompleteWhenPipelineCompletesAndSyncTargetManagerShouldNotContinue() {
    final CompletableFuture<Void> pipelineFuture = new CompletableFuture<>();
    when(syncTargetManager.findSyncTarget()).thenReturn(completedFuture(syncTarget));
    final CompletableFuture<Void> result = chainDownloader.start();

    verify(syncTargetManager).findSyncTarget();

    pipelineFuture.complete(null);

    verify(syncTargetManager, Mockito.times(2)).shouldContinueDownloading();
    verify(syncState).clearSyncTarget();
    verifyNoMoreInteractions(syncTargetManager);
    assertThat(result).isCompleted();
  }

  @Test
  public void shouldSelectNewSyncTargetWhenPipelineCompletesIfSyncTargetManagerShouldContinue() {
    when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);
    final CompletableFuture<Void> pipelineFuture = expectPipelineStarted(syncTarget);

    final CompletableFuture<Void> result = chainDownloader.start();

    verify(syncTargetManager).findSyncTarget();

    // Setup expectation for second time round.
    expectPipelineStarted(syncTarget2, downloadPipeline2);

    pipelineFuture.complete(null);
    assertThat(result).isNotDone();

    verify(syncTargetManager, times(2)).findSyncTarget();
    assertThat(result).isNotDone();
  }

  @Test
  public void shouldNotNestExceptionHandling() {
    when(syncTargetManager.shouldContinueDownloading())
        .thenReturn(true)
        .thenReturn(true) // Allow continuing after first successful download
        .thenReturn(false); // But not after finding the second sync target fails

    final CompletableFuture<SyncTarget> findSecondSyncTargetFuture = new CompletableFuture<>();
    final CompletableFuture<Void> pipelineFuture = expectPipelineStarted(syncTarget);

    final CompletableFuture<Void> result = chainDownloader.start();

    verify(syncTargetManager).findSyncTarget();

    // Setup expectation for second call
    when(syncTargetManager.findSyncTarget()).thenReturn(findSecondSyncTargetFuture);

    pipelineFuture.complete(null);
    assertThat(result).isNotDone();

    verify(syncTargetManager, times(2)).findSyncTarget();
    assertThat(result).isNotDone();

    final RuntimeException exception = new RuntimeException("Nope");
    findSecondSyncTargetFuture.completeExceptionally(exception);

    assertExceptionallyCompletedWith(result, exception);
    // Should only need to check if it should continue twice.
    // We'll wind up doing this check more than necessary if we keep wrapping additional exception
    // handlers when restarting the sequence which wastes memory.
    verify(syncTargetManager, times(3)).shouldContinueDownloading();
  }

  @Test
  public void shouldNotStartDownloadIfCancelledWhileSelectingSyncTarget() {
    final CompletableFuture<SyncTarget> selectSyncTargetFuture = new CompletableFuture<>();
    lenient().when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);
    when(syncTargetManager.findSyncTarget()).thenReturn(selectSyncTargetFuture);

    final CompletableFuture<Void> result = chainDownloader.start();
    verify(syncTargetManager).findSyncTarget();

    chainDownloader.cancel();
    // Note the future doesn't complete until all activity has come to a stop.
    assertThat(result).isNotDone();

    selectSyncTargetFuture.complete(syncTarget);
    verifyNoInteractions(downloadPipelineFactory);
    assertCancelled(result);
  }

  @Test
  public void shouldAbortPipelineIfCancelledAfterDownloadStarts() {
    lenient().when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);
    final CompletableFuture<Void> pipelineFuture = expectPipelineStarted(syncTarget);

    final CompletableFuture<Void> result = chainDownloader.start();
    verify(syncTargetManager).findSyncTarget();
    verify(downloadPipelineFactory).createDownloadPipelineForSyncTarget(syncTarget);
    verify(downloadPipelineFactory)
        .startPipeline(scheduler, syncState, syncTarget, downloadPipeline);

    chainDownloader.cancel();
    // Pipeline is aborted immediately.
    verify(downloadPipeline).abort();
    // The future doesn't complete until all activity has come to a stop.
    assertThat(result).isNotDone();

    // And then things are complete when the pipeline is actually complete.
    pipelineFuture.completeExceptionally(new CancellationException("Pipeline aborted"));
    assertCancelled(result);
  }

  @Test
  public void shouldRetryIfNotCancelledAndPipelineTaskThrowsException() {
    immediatelyCompletePauseAfterError();
    when(syncTargetManager.shouldContinueDownloading()).thenReturn(true);

    final CompletableFuture<Void> pipelineFuture1 = new CompletableFuture<>();
    final CompletableFuture<Void> pipelineFuture2 = new CompletableFuture<>();

    when(syncTargetManager.findSyncTarget())
        .thenReturn(completedFuture(syncTarget))
        .thenReturn(completedFuture(syncTarget2));

    expectPipelineCreation(syncTarget2, downloadPipeline2, pipelineFuture2);

    final CompletableFuture<Void> result = chainDownloader.start();

    // A task in the pipeline is cancelled, causing the pipeline to abort.
    final Exception taskException =
        new RuntimeException(
            "Async operation failed", new ExecutionException(new CancellationException()));
    pipelineFuture1.completeExceptionally(taskException);

    // Second time round, there are no task errors in the pipeline, and the download can complete.
    pipelineFuture2.complete(null);

    assertThat(result).isDone();
  }

  @Test
  public void shouldDisconnectSyncTargetOnInvalidBlockException_finishedDownloading_cancelled() {
    testInvalidBlockHandling(true, true);
  }

  @Test
  public void
      shouldDisconnectSyncTargetOnInvalidBlockException_notFinishedDownloading_notCancelled() {
    testInvalidBlockHandling(false, false);
  }

  @Test
  public void shouldDisconnectSyncTargetOnInvalidBlockException_finishedDownloading_notCancelled() {
    testInvalidBlockHandling(true, false);
  }

  @Test
  public void shouldDisconnectSyncTargetOnInvalidBlockException_notFinishedDownloading_cancelled() {
    testInvalidBlockHandling(false, true);
  }

  public void testInvalidBlockHandling(
      final boolean isFinishedDownloading, final boolean isCancelled) {
    final CompletableFuture<SyncTarget> selectTargetFuture = new CompletableFuture<>();
    when(syncTargetManager.findSyncTarget())
        .thenReturn(selectTargetFuture)
        .thenReturn(new CompletableFuture<>());

    chainDownloader.start();
    verify(syncTargetManager).findSyncTarget();
    if (isCancelled) {
      chainDownloader.cancel();
    }
    selectTargetFuture.completeExceptionally(InvalidBlockException.create("Failed"));

    verify(syncState, times(1))
        .disconnectSyncTarget(DisconnectReason.BREACH_OF_PROTOCOL_INVALID_BLOCK);
  }

  private CompletableFuture<Void> expectPipelineStarted(final SyncTarget syncTarget) {
    return expectPipelineStarted(syncTarget, downloadPipeline);
  }

  private CompletableFuture<Void> expectPipelineStarted(
      final SyncTarget syncTarget, final Pipeline<?> pipeline) {
    final CompletableFuture<Void> pipelineFuture = new CompletableFuture<>();
    when(syncTargetManager.findSyncTarget()).thenReturn(completedFuture(syncTarget));
    expectPipelineCreation(syncTarget, pipeline, pipelineFuture);
    return pipelineFuture;
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // Mockito really doesn't like Pipeline<?>
  private void expectPipelineCreation(
      final SyncTarget syncTarget,
      final Pipeline<?> pipeline,
      final CompletableFuture<Void> completableFuture) {
    when(downloadPipelineFactory.createDownloadPipelineForSyncTarget(syncTarget))
        .thenReturn((Pipeline) pipeline);
    when(downloadPipelineFactory.startPipeline(scheduler, syncState, syncTarget, pipeline))
        .thenReturn(completableFuture);
  }

  private void assertExceptionallyCompletedWith(
      final CompletableFuture<?> future, final Throwable error) {
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class).hasRootCause(error);
  }

  private void assertCancelled(final CompletableFuture<?> future) {
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(CancellationException.class);
  }

  @SuppressWarnings("unchecked")
  private void immediatelyCompletePauseAfterError() {
    when(scheduler.scheduleFutureTask(
            any(Supplier.class), same(PipelineChainDownloader.PAUSE_AFTER_ERROR_DURATION)))
        .then(invocation -> ((Supplier<CompletableFuture<?>>) invocation.getArgument(0)).get());
  }
}
