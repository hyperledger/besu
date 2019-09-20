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
import static org.hyperledger.besu.util.FutureUtils.completedExceptionally;
import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;

import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.exceptions.EthTaskException;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncTarget;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.util.ExceptionUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PipelineChainDownloader<C> implements ChainDownloader {
  private static final Logger LOG = LogManager.getLogger();
  static final Duration PAUSE_AFTER_ERROR_DURATION = Duration.ofSeconds(2);
  private final SyncState syncState;
  private final SyncTargetManager<C> syncTargetManager;
  private final DownloadPipelineFactory downloadPipelineFactory;
  private final EthScheduler scheduler;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final Counter pipelineCompleteCounter;
  private final Counter pipelineErrorCounter;
  private Pipeline<?> currentDownloadPipeline;

  public PipelineChainDownloader(
      final SyncState syncState,
      final SyncTargetManager<C> syncTargetManager,
      final DownloadPipelineFactory downloadPipelineFactory,
      final EthScheduler scheduler,
      final MetricsSystem metricsSystem) {
    this.syncState = syncState;
    this.syncTargetManager = syncTargetManager;
    this.downloadPipelineFactory = downloadPipelineFactory;
    this.scheduler = scheduler;

    final LabelledMetric<Counter> labelledCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "chain_download_pipeline_restarts",
            "Number of times the chain download pipeline has been restarted",
            "reason");
    pipelineCompleteCounter = labelledCounter.labels("complete");
    pipelineErrorCounter = labelledCounter.labels("error");
  }

  @Override
  public CompletableFuture<Void> start() {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("Cannot start a chain download twice");
    }
    return performDownload();
  }

  @Override
  public synchronized void cancel() {
    cancelled.set(true);
    if (currentDownloadPipeline != null) {
      currentDownloadPipeline.abort();
    }
  }

  private CompletableFuture<Void> performDownload() {
    return exceptionallyCompose(selectSyncTargetAndDownload(), this::handleFailedDownload)
        .thenCompose(this::repeatUnlessDownloadComplete);
  }

  private CompletableFuture<Void> selectSyncTargetAndDownload() {
    return syncTargetManager
        .findSyncTarget(Optional.empty())
        .thenCompose(this::startDownloadForSyncTarget)
        .thenRun(pipelineCompleteCounter::inc);
  }

  private CompletionStage<Void> repeatUnlessDownloadComplete(
      @SuppressWarnings("unused") final Void result) {
    syncState.clearSyncTarget();
    if (syncTargetManager.shouldContinueDownloading()) {
      return performDownload();
    } else {
      LOG.info("Chain download complete");
      return completedFuture(null);
    }
  }

  private CompletionStage<Void> handleFailedDownload(final Throwable error) {
    pipelineErrorCounter.inc();
    if (ExceptionUtils.rootCause(error) instanceof InvalidBlockException) {
      LOG.warn(
          "Invalid block detected.  Disconnecting from sync target. {}",
          ExceptionUtils.rootCause(error).getMessage());
      syncState.disconnectSyncTarget(DisconnectReason.BREACH_OF_PROTOCOL);
    }

    if (!cancelled.get()
        && syncTargetManager.shouldContinueDownloading()
        && !(ExceptionUtils.rootCause(error) instanceof CancellationException)) {
      logDownloadFailure("Chain download failed. Restarting after short delay.", error);
      // Allowing the normal looping logic to retry after a brief delay.
      return scheduler.scheduleFutureTask(() -> completedFuture(null), PAUSE_AFTER_ERROR_DURATION);
    }

    logDownloadFailure("Chain download failed.", error);
    // Propagate the error out, terminating this chain download.
    return completedExceptionally(error);
  }

  private void logDownloadFailure(final String message, final Throwable error) {
    final Throwable rootCause = ExceptionUtils.rootCause(error);
    if (rootCause instanceof CancellationException || rootCause instanceof InterruptedException) {
      LOG.trace(message, error);
    } else if (rootCause instanceof EthTaskException) {
      LOG.debug(message, error);
    } else if (rootCause instanceof InvalidBlockException) {
      LOG.warn(message, error);
    } else {
      LOG.error(message, error);
    }
  }

  private synchronized CompletionStage<Void> startDownloadForSyncTarget(final SyncTarget target) {
    if (cancelled.get()) {
      return completedExceptionally(new CancellationException("Chain download was cancelled"));
    }
    syncState.setSyncTarget(target.peer(), target.commonAncestor());
    currentDownloadPipeline = downloadPipelineFactory.createDownloadPipelineForSyncTarget(target);
    return scheduler.startPipeline(currentDownloadPipeline);
  }
}
