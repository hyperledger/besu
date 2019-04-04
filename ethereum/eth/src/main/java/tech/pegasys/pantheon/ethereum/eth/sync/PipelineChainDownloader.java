/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.sync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static tech.pegasys.pantheon.util.FutureUtils.completedExceptionally;
import static tech.pegasys.pantheon.util.FutureUtils.exceptionallyCompose;

import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.services.pipeline.Pipeline;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PipelineChainDownloader<C> implements ChainDownloader {
  private static final Logger LOG = LogManager.getLogger();
  private final SyncTargetManager<C> syncTargetManager;
  private final DownloadPipelineFactory downloadPipelineFactory;
  private final EthScheduler scheduler;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final Counter pipelineCompleteCounter;
  private final Counter pipelineErrorCounter;
  private Pipeline<?> currentDownloadPipeline;

  public PipelineChainDownloader(
      final SyncTargetManager<C> syncTargetManager,
      final DownloadPipelineFactory downloadPipelineFactory,
      final EthScheduler scheduler,
      final MetricsSystem metricsSystem) {
    this.syncTargetManager = syncTargetManager;
    this.downloadPipelineFactory = downloadPipelineFactory;
    this.scheduler = scheduler;

    final LabelledMetric<Counter> labelledCounter =
        metricsSystem.createLabelledCounter(
            MetricCategory.SYNCHRONIZER,
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
    if (syncTargetManager.shouldContinueDownloading()) {
      return performDownload();
    } else {
      LOG.info("Chain download complete");
      return completedFuture(null);
    }
  }

  private CompletionStage<Void> handleFailedDownload(final Throwable error) {
    LOG.debug("Chain download failed. Will restart if required.", error);
    pipelineErrorCounter.inc();
    if (!cancelled.get() && syncTargetManager.shouldContinueDownloading()) {
      // Drop the error, allowing the normal looping logic to retry.
      return completedFuture(null);
    }
    // Propagate the error out, terminating this chain download.
    return completedExceptionally(error);
  }

  private synchronized CompletionStage<Void> startDownloadForSyncTarget(final SyncTarget target) {
    if (cancelled.get()) {
      return completedExceptionally(new CancellationException("Chain download was cancelled"));
    }
    currentDownloadPipeline = downloadPipelineFactory.createDownloadPipelineForSyncTarget(target);
    return scheduler.startPipeline(currentDownloadPipeline);
  }
}
