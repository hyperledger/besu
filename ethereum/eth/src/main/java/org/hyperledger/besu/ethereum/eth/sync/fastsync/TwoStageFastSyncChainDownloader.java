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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two-stage fast sync chain downloader that orchestrates:
 *
 * <p>Stage 1: Backward header download from pivot block to stop block Stage 2: Forward
 * bodies/receipts download from start block to pivot block
 *
 * <p>This approach allows efficient syncing by downloading headers in parallel out-of-order, then
 * importing bodies and receipts in forward order.
 */
public class TwoStageFastSyncChainDownloader implements ChainDownloader {
  private static final Logger LOG = LoggerFactory.getLogger(TwoStageFastSyncChainDownloader.class);

  private final FastSyncDownloadPipelineFactory pipelineFactory;
  private final EthScheduler scheduler;
  private final Hash pivotBlockHash;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final SyncState syncState;
  private final FastSyncState fastSyncState;
  private final FastSyncStateStorage fastSyncStateStorage;

  private volatile Pipeline<?> currentPipeline;

  /**
   * Creates a new TwoStageFastSyncChainDownloader.
   *
   * @param pipelineFactory the pipeline factory for creating download pipelines
   * @param scheduler the scheduler for running pipelines
   * @param syncState the sync state tracker
   * @param pivotBlockHash the pivot block hash
   * @param metricsSystem the metrics system (unused but kept for API compatibility)
   * @param syncDurationMetrics the sync duration metrics tracker (unused but kept for API
   *     compatibility)
   * @param fastSyncState the fast sync state for tracking progress
   * @param fastSyncStateStorage the storage for persisting fast sync state
   */
  public TwoStageFastSyncChainDownloader(
      final FastSyncDownloadPipelineFactory pipelineFactory,
      final EthScheduler scheduler,
      final SyncState syncState,
      final Hash pivotBlockHash,
      final MetricsSystem metricsSystem,
      final SyncDurationMetrics syncDurationMetrics,
      final FastSyncState fastSyncState,
      final FastSyncStateStorage fastSyncStateStorage) {
    this.pipelineFactory = pipelineFactory;
    this.scheduler = scheduler;
    this.pivotBlockHash = pivotBlockHash;
    this.syncState = syncState;
    this.fastSyncState = fastSyncState;
    this.fastSyncStateStorage = fastSyncStateStorage;
  }

  @Override
  public CompletableFuture<Void> start() {
    LOG.info("Starting two-stage fast sync chain download from pivot block {}", pivotBlockHash);

    final Instant overallStartTime = Instant.now();

    // Check if we need to run Stage 1 (backward header download)
    final CompletableFuture<Void> stage1Future = determineStage1Execution();

    return stage1Future
        .thenCompose(
            ignore -> {
              if (cancelled.get()) {
                LOG.info("Two-stage sync cancelled after Stage 1");
                return CompletableFuture.failedFuture(new CancellationException());
              }
              // Stage 2: Forward bodies and receipts download
              return runStage2ForwardBodiesAndReceipts();
            })
        .handle(
            (ignored, throwable) -> {
              if (throwable != null) {
                if (throwable instanceof CancellationException) {
                  LOG.info("Two-stage fast sync chain download cancelled");
                } else {
                  LOG.error("Two-stage fast sync chain download failed", throwable);
                }
                return CompletableFuture.<Void>failedFuture(throwable);
              } else {
                final Duration totalDuration = Duration.between(overallStartTime, Instant.now());
                LOG.info(
                    "Two-stage fast sync chain download complete in {} seconds",
                    totalDuration.getSeconds());
                return CompletableFuture.<Void>completedFuture(null);
              }
            })
        .thenCompose(f -> f);
  }

  /**
   * Determines whether Stage 1 (backward header download) needs to run based on persisted state.
   *
   * @return CompletableFuture that completes when Stage 1 is done (or skipped)
   */
  private CompletableFuture<Void> determineStage1Execution() {
    // Check if backward header download already completed
    if (fastSyncState.isBackwardHeaderDownloadComplete()) {
      //      // Check if pivot has changed
      //      final Hash storedPivotHash = fastSyncState.getPivotBlockHash().orElse(null);
      //
      //      if (pivotBlockHash.equals(storedPivotHash)) {
      //        LOG.info(
      //            "Backward header download already complete for pivot {}. Skipping Stage 1.",
      //            pivotBlockHash);
      return CompletableFuture.completedFuture(null);
      //      } else {
      //        LOG.info(
      //            "Pivot block changed from {} to {}. Running incremental backward header
      // download.",
      //            storedPivotHash,
      //            pivotBlockHash);
      //        // TODO: Implement incremental download between old and new pivot
      //        // For now, treat as resume from lowest point
      //        return runStage1BackwardHeaderDownload();
      //      }
    } else {
      // Backward download not complete - run or resume Stage 1
      return runStage1BackwardHeaderDownload();
    }
  }

  private CompletableFuture<Void> runStage1BackwardHeaderDownload() {
    LOG.info("Stage 1: Starting backward header download from pivot block {}", pivotBlockHash);

    final Instant stage1StartTime = Instant.now();

    final Pipeline<Long> headerPipeline =
        pipelineFactory.createBackwardHeaderDownloadPipeline(fastSyncState, fastSyncStateStorage);
    currentPipeline = headerPipeline;

    return scheduler
        .startPipeline(headerPipeline)
        .thenApply(
            ignore -> {
              final Duration stage1Duration = Duration.between(stage1StartTime, Instant.now());
              LOG.info(
                  "Stage 1 complete: Backward header download finished in {} seconds",
                  stage1Duration.getSeconds());

              // Mark backward header download as complete and persist
              fastSyncState.setBackwardHeaderDownloadComplete(true);
              fastSyncStateStorage.storeState(fastSyncState);
              LOG.info("Persisted backward header download completion state");

              return null;
            });
  }

  private CompletableFuture<Void> runStage2ForwardBodiesAndReceipts() {
    LOG.info("Stage 2: Starting forward bodies and receipts download to pivot block");

    final Instant stage2StartTime = Instant.now();

    // Use the pivot block number from the persisted state (from Stage 1)
    final long pivotBlockNumber =
        fastSyncState
            .getPivotBlockNumber()
            .orElseThrow(
                () -> new IllegalStateException("Pivot block number not available for Stage 2"));

    final Pipeline<List<BlockHeader>> bodiesReceiptsPipeline =
        pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(pivotBlockNumber, syncState);
    currentPipeline = bodiesReceiptsPipeline;

    return scheduler
        .startPipeline(bodiesReceiptsPipeline)
        .thenApply(
            ignore -> {
              final Duration stage2Duration = Duration.between(stage2StartTime, Instant.now());
              LOG.info(
                  "Stage 2 complete: Forward bodies/receipts download finished in {} seconds",
                  stage2Duration.getSeconds());
              return null;
            });
  }

  @Override
  public void cancel() {
    LOG.info("Cancelling two-stage fast sync chain download");
    cancelled.set(true);

    final Pipeline<?> pipeline = currentPipeline;
    if (pipeline != null) {
      pipeline.abort();
    }
  }
}
