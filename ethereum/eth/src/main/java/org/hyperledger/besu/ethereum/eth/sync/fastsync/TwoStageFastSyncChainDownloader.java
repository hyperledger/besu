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
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two-stage fast sync chain downloader that orchestrates:
 *
 * <p>Stage 1: Backward header download from pivot block to stop block
 *
 * <p>Stage 2: Forward bodies/receipts download from start block to pivot block
 *
 * <p>Supports incremental continuation when the world state downloader updates the pivot block,
 * avoiding re-downloading already synced data.
 */
public class TwoStageFastSyncChainDownloader implements ChainDownloader, PivotUpdateListener {
  private static final Logger LOG = LoggerFactory.getLogger(TwoStageFastSyncChainDownloader.class);

  private final FastSyncDownloadPipelineFactory pipelineFactory;
  private final EthScheduler scheduler;
  private final SyncState syncState;
  private final SyncDurationMetrics syncDurationMetrics;
  private final ChainSyncStateStorage chainStateStorage;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<ChainSyncState> chainState;
  private final AtomicReference<BlockHeader> pendingPivotUpdate = new AtomicReference<>(null);

  private volatile Pipeline<?> currentPipeline;

  /**
   * Creates a new TwoStageFastSyncChainDownloader.
   *
   * @param pipelineFactory the pipeline factory for creating download pipelines
   * @param scheduler the scheduler for running pipelines
   * @param syncState the sync state tracker
   * @param pivotBlockHash the initial pivot block hash (kept for logging compatibility)
   * @param metricsSystem the metrics system (unused but kept for API compatibility)
   * @param syncDurationMetrics the sync duration metrics tracker
   * @param initialPivotHeader the initial pivot block header
   * @param chainStateStorage the storage for chain sync state
   * @param checkpointBlock the checkpoint block number (0 for full sync)
   */
  public TwoStageFastSyncChainDownloader(
      final FastSyncDownloadPipelineFactory pipelineFactory,
      final EthScheduler scheduler,
      final SyncState syncState,
      final Hash pivotBlockHash,
      final MetricsSystem metricsSystem,
      final SyncDurationMetrics syncDurationMetrics,
      final BlockHeader initialPivotHeader,
      final ChainSyncStateStorage chainStateStorage,
      final long checkpointBlock) {
    this.pipelineFactory = pipelineFactory;
    this.scheduler = scheduler;
    this.syncState = syncState;
    this.syncDurationMetrics = syncDurationMetrics;
    this.chainStateStorage = chainStateStorage;

    // Initialize or load chain sync state
    ChainSyncState chainSyncState =
        chainStateStorage.loadState(
            rlpInput -> BlockHeader.readFrom(rlpInput, pipelineFactory.getBlockHeaderFunctions()));
    if (chainSyncState == null) {
      // First time sync - create initial state
      chainSyncState = ChainSyncState.initialSync(initialPivotHeader, checkpointBlock);
      chainStateStorage.storeState(chainSyncState);
      LOG.info(
          "Created initial chain sync state: pivot={}, checkpoint={}",
          initialPivotHeader.getNumber(),
          checkpointBlock);
    } else {
      LOG.info("Loaded existing chain sync state: {}", chainSyncState);
    }

    this.chainState = new AtomicReference<>(chainSyncState);
  }

  @Override
  public void onPivotUpdated(final BlockHeader newPivotBlockHeader) {
    pendingPivotUpdate.getAndSet(newPivotBlockHeader);
    LOG.info("Received pivot update from block no {}", newPivotBlockHeader.getNumber());
  }

  @Override
  public CompletableFuture<Void> start() {
    final ChainSyncState initialState = chainState.get();
    LOG.info(
        "Starting two-stage fast sync chain download from pivot block {}",
        initialState.getPivotBlockHash());

    final Instant overallStartTime = Instant.now();

    // Start chain download duration metrics
    syncDurationMetrics.startTimer(SyncDurationMetrics.Labels.CHAIN_DOWNLOAD_DURATION);

    return runSyncWithRetry(initialState.getPivotBlockNumber())
        .handle(
            (ignored, throwable) -> {
              if (throwable != null) {
                if (throwable instanceof CancellationException) {
                  LOG.info("Two-stage fast sync chain download cancelled");
                } else {
                  LOG.error("Two-stage fast sync chain download failed", throwable);
                }
                // Stop metrics on failure
                syncDurationMetrics.stopTimer(SyncDurationMetrics.Labels.CHAIN_DOWNLOAD_DURATION);
                return CompletableFuture.<Void>failedFuture(throwable);
              } else {
                final Duration totalDuration = Duration.between(overallStartTime, Instant.now());
                LOG.info(
                    "Two-stage fast sync chain download complete in {} seconds",
                    totalDuration.getSeconds());
                // Stop metrics on success
                syncDurationMetrics.stopTimer(SyncDurationMetrics.Labels.CHAIN_DOWNLOAD_DURATION);
                return CompletableFuture.<Void>completedFuture(null);
              }
            })
        .thenCompose(f -> f);
  }

  private CompletableFuture<Void> runSyncWithRetry(final long initialPivotNumber) {
    return determineStage1Execution()
        .thenCompose(
            ignore -> {
              if (cancelled.get()) {
                LOG.info("Two-stage sync cancelled after Stage 1");
                return CompletableFuture.failedFuture(new CancellationException());
              }
              return runStage2ForwardBodiesAndReceipts();
            })
        .thenCompose(ignore -> checkAndHandlePivotUpdate(initialPivotNumber))
        .handle(
            (result, error) -> {
              if (error != null && shouldRetry(error)) {
                LOG.warn("Chain sync encountered error, will retry from saved state", error);
                // State is already persisted, restart from current position
                return runSyncWithRetry(chainState.get().getPivotBlockNumber());
              } else if (error != null) {
                return CompletableFuture.<Void>failedFuture(error);
              }
              return CompletableFuture.<Void>completedFuture(result);
            })
        .thenCompose(f -> f);
  }

  private boolean shouldRetry(final Throwable error) {
    final Throwable cause = error instanceof CompletionException ? error.getCause() : error;
    return !(cause instanceof CancellationException);
  }

  /**
   * Determines whether Stage 1 (backward header download) needs to run based on the persisted
   * state.
   *
   * @return CompletableFuture that completes when Stage 1 is done (or skipped)
   */
  private CompletableFuture<Void> determineStage1Execution() {
    final ChainSyncState current = chainState.get();

    if (current.isHeadersDownloadComplete()) {
      LOG.info(
          "Backward header download already complete for pivot {}. Skipping Stage 1.",
          current.getPivotBlockNumber());
      return CompletableFuture.completedFuture(null);
    } else {
      return runStage1BackwardHeaderDownload();
    }
  }

  private CompletableFuture<Void> runStage1BackwardHeaderDownload() {
    final ChainSyncState current = chainState.get();
    LOG.info(
        "Stage 1: Starting backward header download from pivot {} to stop block {}",
        current.getPivotBlockNumber(),
        current.getHeaderDownloadStopBlock());

    final Instant stage1StartTime = Instant.now();

    final Pipeline<Long> headerPipeline =
        pipelineFactory.createBackwardHeaderDownloadPipeline(chainState, chainStateStorage);
    currentPipeline = headerPipeline;

    return scheduler
        .startPipeline(headerPipeline)
        .thenApply(
            ignore -> {
              final Duration stage1Duration = Duration.between(stage1StartTime, Instant.now());
              LOG.info(
                  "Stage 1 complete: Backward header download finished in {} seconds",
                  stage1Duration.getSeconds());

              // Mark headers download as complete and persist
              chainState.updateAndGet(state -> state.withHeadersDownloadComplete());
              chainStateStorage.storeState(chainState.get());
              LOG.info("Persisted backward header download completion state");

              return null;
            });
  }

  private CompletableFuture<Void> runStage2ForwardBodiesAndReceipts() {
    final ChainSyncState current = chainState.get();
    LOG.info(
        "Stage 2: Starting forward bodies and receipts download from {} to pivot {}",
        current.getBodiesDownloadStartBlock(),
        current.getPivotBlockNumber());

    final Instant stage2StartTime = Instant.now();

    final Pipeline<List<BlockHeader>> bodiesReceiptsPipeline =
        pipelineFactory.createForwardBodiesAndReceiptsDownloadPipelineFromTo(
            current.getBodiesDownloadStartBlock(), current.getPivotBlockNumber(), syncState);
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

  /**
   * Checks if the pivot block has been updated during sync and handles continuation if necessary.
   * Uses double-check pattern to avoid race conditions.
   *
   * @param previousPivotBlockNumber the pivot block number when sync started
   * @return CompletableFuture that completes when all continuation is done
   */
  private CompletableFuture<Void> checkAndHandlePivotUpdate(final long previousPivotBlockNumber) {

    final BlockHeader updatedPivot = pendingPivotUpdate.getAndSet(null);

    if (updatedPivot != null && updatedPivot.getNumber() > previousPivotBlockNumber) {
      LOG.info(
          "Pivot block has been updated from {} to {}. Continuing sync to new pivot.",
          previousPivotBlockNumber,
          updatedPivot.getNumber());

      // Update chain state to new pivot
      chainState.updateAndGet(
          state -> state.continueToNewPivot(updatedPivot, previousPivotBlockNumber));
      chainStateStorage.storeState(chainState.get());

      return continueToNewPivot(previousPivotBlockNumber, updatedPivot.getNumber());
    }

    LOG.info(
        "No pivot block update detected (current: {}), sync complete", previousPivotBlockNumber);
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Continues syncing from the previous pivot to a new updated pivot. Reuses the standard stage 1
   * and stage 2 methods since chainState has already been updated with new boundaries.
   *
   * @param previousPivotBlockNumber the previous pivot block number (for logging)
   * @param newPivotBlockNumber the new pivot block number (for recursive check)
   * @return CompletableFuture that completes when continuation is done
   */
  private CompletableFuture<Void> continueToNewPivot(
      final long previousPivotBlockNumber, final long newPivotBlockNumber) {

    LOG.info(
        "Continuation: Syncing from previous pivot {} to new pivot {}",
        previousPivotBlockNumber,
        newPivotBlockNumber);

    // ChainState has already been updated with new pivot and boundaries in
    // checkAndHandlePivotUpdate
    // Just run the standard stage 1 and stage 2 methods which will use the updated boundaries
    return runStage1BackwardHeaderDownload()
        .thenCompose(
            ignore -> {
              if (cancelled.get()) {
                return CompletableFuture.failedFuture(new CancellationException());
              }
              return runStage2ForwardBodiesAndReceipts();
            })
        .thenCompose(
            ignore -> {
              // Recursively check for further pivot updates
              return checkAndHandlePivotUpdate(newPivotBlockNumber);
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
