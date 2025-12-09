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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncState.downloadCheckpointHeader;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.ChainDownloader;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncState;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.ChainSyncStateStorage;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotUpdateListener;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.WorldStateHealFinishedListener;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
public class SnapSyncChainDownloader
    implements ChainDownloader, PivotUpdateListener, WorldStateHealFinishedListener {
  private static final Logger LOG = LoggerFactory.getLogger(SnapSyncChainDownloader.class);

  private final SnapSyncChainDownloadPipelineFactory pipelineFactory;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final SyncDurationMetrics syncDurationMetrics;
  private final ChainSyncStateStorage chainSyncStateStorage;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<ChainSyncState> chainSyncState;
  private final AtomicReference<BlockHeader> pendingPivotUpdate = new AtomicReference<>(null);
  private CompletableFuture<Void> pivotUpdateFuture = new CompletableFuture<>();
  private final CompletableFuture<Void> worldStateHealFinishedFuture = new CompletableFuture<>();

  private final AtomicBoolean downloadInProgress = new AtomicBoolean(false);

  private volatile Pipeline<?> currentPipeline;
  private Instant overallStartTime;

  /**
   * Creates a new TwoStageFastSyncChainDownloader. The first stage is to download all headers from
   * a safe pivot down to the genesis. Due to the chain of parent hashes in the headers, as well as
   * the trusted pivot and the known genesis, we can be sure that we can trust the downloaded
   * headers.
   *
   * <p>The second stage is to download the bodies and receipts. Bodies and receipts are validated
   * by checking the transactions root and the receipts root against the values contained in th
   * trusted headers from the first stage. Bodies and receipts might not be downloaded from genesis,
   * but from a checkpoint block, e.g. for mainnet from the merge block.
   *
   * <p>Once the second stage is completed we start downloading headers, bodies, and receipts, when
   * the pivot block is updated. In this case we download the headers from the new pivot down to the
   * new pivot block, followed by the bodies and receipts from the old pivot block to the new pivot
   * block.
   *
   * @param pipelineFactory the pipeline factory for creating download pipelines
   * @param protocolContext the protocol context providing access to blockchain
   * @param ethContext the ethContext for running pipelines
   * @param syncState the sync state tracker
   * @param metricsSystem the metrics system (unused but kept for API compatibility)
   * @param syncDurationMetrics the sync duration metrics tracker
   * @param initialPivotHeader the initial pivot block header
   * @param chainStateStorage the storage for chain sync state
   */
  public SnapSyncChainDownloader(
      final SnapSyncChainDownloadPipelineFactory pipelineFactory,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem,
      final SyncDurationMetrics syncDurationMetrics,
      final BlockHeader initialPivotHeader,
      final ChainSyncStateStorage chainStateStorage) {
    this.pipelineFactory = pipelineFactory;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.syncState = syncState;
    this.syncDurationMetrics = syncDurationMetrics;
    this.chainSyncStateStorage = chainStateStorage;

    // Initialize or load chain sync state
    ChainSyncState chainSyncState =
        chainStateStorage.loadState(
            rlpInput ->
                BlockHeader.readFrom(
                    rlpInput, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)));
    if (chainSyncState == null) {
      // First time sync - create initial state
      // This downloads headers from the pivot down to the genesis block

      final Optional<Checkpoint> maybeCheckpoint = syncState.getCheckpoint();
      Hash checkpointHash;
      final BlockHeader checkpointBlockHeader;
      final MutableBlockchain blockchain = protocolContext.getBlockchain();
      if (maybeCheckpoint.isEmpty()) {
        // the current head of chain is the lower trust anchor
        checkpointBlockHeader = blockchain.getChainHeadHeader();
        checkpointHash = checkpointBlockHeader.getHash();
        LOG.debug(
            "No checkpoint found, using current chain head as lower trust anchor: {}, {}",
            checkpointBlockHeader.getNumber(),
            checkpointHash);
      } else {
        // use the checkpoint as the lower trust anchor
        final Checkpoint checkpoint = maybeCheckpoint.get();
        checkpointHash = checkpoint.blockHash();
        Difficulty checkpointDifficulty = checkpoint.totalDifficulty();
        checkpointBlockHeader =
            getCheckpointBlockHeader(protocolSchedule, protocolContext, ethContext, checkpointHash);
        blockchain.unsafeSetChainHead(checkpointBlockHeader, checkpointDifficulty);
        blockchain.unsafeStoreHeader(checkpointBlockHeader, checkpointDifficulty);
        LOG.debug(
            "Using checkpoint {} as lower trust anchor: {}, {}",
            checkpoint.blockNumber(),
            checkpoint.blockHash(),
            checkpoint.totalDifficulty());
      }

      final BlockHeader genesisBlockHeader = blockchain.getGenesisBlockHeader();

      chainSyncState =
          ChainSyncState.initialSync(initialPivotHeader, checkpointBlockHeader, genesisBlockHeader);

      LOG.info("Created initial chain sync state: {}", chainSyncState);
    } else {
      LOG.info("Loaded existing chain sync state: {}", chainSyncState);
    }

    this.chainSyncState = new AtomicReference<>(chainSyncState);
  }

  @Override
  public void onPivotUpdated(final BlockHeader newPivotBlockHeader) {
    pendingPivotUpdate.getAndSet(newPivotBlockHeader);
    LOG.info("Received pivot update from block no {}", newPivotBlockHeader.getNumber());
    pivotUpdateFuture.complete(null);
  }

  @Override
  public void onWorldStateHealFinished() {
    LOG.info("World state download is stable, no more pivot updates expected");
    worldStateHealFinishedFuture.complete(null);
  }

  @Override
  public CompletableFuture<Void> start() {
    final ChainSyncState initialState = chainSyncState.get();
    final BlockHeader pivotBlockHeader = initialState.pivotBlockHeader();
    final BlockHeader checkpointBlockHeader = initialState.blockDownloadAnchor();
    LOG.info(
        "Starting two-stage fast sync chain download from pivot block {}, {}, and checkpoint block {}.",
        pivotBlockHeader.getHash(),
        pivotBlockHeader.getNumber(),
        checkpointBlockHeader.getNumber());

    overallStartTime = Instant.now();

    syncDurationMetrics.startTimer(SyncDurationMetrics.Labels.CHAIN_DOWNLOAD_DURATION);

    return downloadAccordingToChainState()
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
                    "Two-stage fast sync chain download finished in {} seconds (including pivot updates)",
                    totalDuration.getSeconds());
                // Stop metrics on success
                syncDurationMetrics.stopTimer(SyncDurationMetrics.Labels.CHAIN_DOWNLOAD_DURATION);
                return CompletableFuture.<Void>completedFuture(null);
              }
            })
        .thenCompose(f -> f);
  }

  private boolean shouldRetry(final Throwable error) {
    final Throwable cause = error instanceof CompletionException ? error.getCause() : error;
    return !(cause instanceof CancellationException);
  }

  /**
   * Determines whether Stage 1 (backward header download) needs to run based on the current chain
   * sync state.
   *
   * @param state the chain sync state to use for this stage
   * @return CompletableFuture that completes when Stage 1 is done (or skipped)
   */
  private CompletableFuture<Void> determineStage1Execution(final ChainSyncState state) {
    if (state.headersDownloadComplete()) {
      LOG.debug(
          "Backward header download already complete for pivot {}. Skipping Stage 1.",
          state.pivotBlockHeader().getNumber());
      return CompletableFuture.completedFuture(null);
    } else {
      return runStage1BackwardHeaderDownload(state);
    }
  }

  private CompletableFuture<Void> runStage1BackwardHeaderDownload(final ChainSyncState state) {
    LOG.debug(
        "Stage 1: Starting backward header download from pivot {} down to stop block {}",
        state.pivotBlockHeader().getNumber(),
        state.headerDownloadAnchor() != null
            ? state.headerDownloadAnchor().getNumber()
            : state.blockDownloadAnchor().getNumber());

    final Instant stage1StartTime = Instant.now();

    final Pipeline<Long> headerPipeline =
        pipelineFactory.createBackwardHeaderDownloadPipeline(state);
    currentPipeline = headerPipeline;

    return ethContext
        .getScheduler()
        .startPipeline(headerPipeline)
        .thenApply(
            ignore -> {
              final Duration stage1Duration = Duration.between(stage1StartTime, Instant.now());
              LOG.debug(
                  "Stage 1 complete: Backward header download finished in {} seconds",
                  stage1Duration.getSeconds());

              // Mark headers download as complete and persist
              chainSyncState.updateAndGet(ChainSyncState::withHeadersDownloadComplete);
              chainSyncStateStorage.storeState(chainSyncState.get());
              LOG.debug("Persisted backward header download completion state");

              return null;
            });
  }

  private CompletableFuture<Void> runStage2ForwardBodiesAndReceipts(final ChainSyncState state) {
    // Always start from current blockchain head (handles fresh start and restart cases)
    final long blockchainHead = protocolContext.getBlockchain().getChainHeadBlockNumber();
    final BlockHeader pivotBlockHeader = state.pivotBlockHeader();
    final long pivotBlockNumber = pivotBlockHeader.getNumber();

    // Validate blockchain head is in expected range
    final long expectedMinStart = state.blockDownloadAnchor().getNumber();
    if (blockchainHead < expectedMinStart) {
      throw new IllegalStateException(
          String.format(
              "Blockchain head (%d) is before expected start (%d). Headers may not have been downloaded yet.",
              blockchainHead, expectedMinStart));
    }

    // Check if already at or past pivot
    if (blockchainHead >= pivotBlockNumber) {
      LOG.debug(
          "Stage 2: Blockchain head ({}) already at or past pivot ({}). Skipping bodies/receipts download.",
          blockchainHead,
          pivotBlockNumber);
      return CompletableFuture.completedFuture(null);
    }

    LOG.debug(
        "Stage 2: Starting forward bodies and receipts download from {} to pivot {}",
        blockchainHead,
        pivotBlockNumber);

    final Instant stage2StartTime = Instant.now();

    final Pipeline<List<BlockHeader>> bodiesAndReceiptsPipeline =
        pipelineFactory.createForwardBodiesAndReceiptsDownloadPipeline(
            blockchainHead, pivotBlockHeader, syncState);
    currentPipeline = bodiesAndReceiptsPipeline;

    return ethContext
        .getScheduler()
        .startPipeline(bodiesAndReceiptsPipeline)
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
   * Starts the chain download process. Only one download can be in progress at a time.
   *
   * @return CompletableFuture that completes when all download cycles are done
   */
  private CompletableFuture<Void> downloadAccordingToChainState() {
    // Guard against concurrent executions
    if (!downloadInProgress.compareAndSet(false, true)) {
      LOG.warn("Download already in progress, ignoring concurrent call");
      return CompletableFuture.failedFuture(
          new IllegalStateException("Download already in progress"));
    }

    final CompletableFuture<Void> result = new CompletableFuture<>();

    // Ensure we always reset the guard when complete
    result.whenComplete((r, error) -> downloadInProgress.set(false));

    // Start the download attempt loop
    attemptDownload(result);

    return result;
  }

  /**
   * Attempts a single download cycle, with retry logic. Non-recursive - schedules next attempt via
   * executor.
   *
   * @param overallResult the future to complete when all attempts are done
   */
  private void attemptDownload(final CompletableFuture<Void> overallResult) {
    if (cancelled.get()) {
      overallResult.completeExceptionally(new CancellationException());
      return;
    }

    // Already completed from another path
    if (overallResult.isDone()) {
      return;
    }

    performSingleDownloadCycle()
        .whenComplete(
            (downloadResult, error) -> {
              if (error != null) {
                handleDownloadError(error, overallResult);
              } else {
                // we are stopping the time after the initial chain download has finished. From now
                // on we are only
                // waiting for new pivots or the world state download to finish.
                syncDurationMetrics.stopTimer(SyncDurationMetrics.Labels.CHAIN_DOWNLOAD_DURATION);
                handlePivotUpdateLoop(overallResult);
              }
            });
  }

  /**
   * Performs a single download cycle: Stage 1 (headers) → Stage 2 (bodies/receipts). Returns when
   * both stages complete (successfully or with error).
   *
   * @return CompletableFuture that completes when the cycle is done
   */
  private CompletableFuture<Void> performSingleDownloadCycle() {
    // Snapshot state once - both stages use the same snapshot
    final ChainSyncState currentState = chainSyncState.get();

    return determineStage1Execution(currentState)
        .thenCompose(
            ignore -> {
              if (cancelled.get()) {
                return CompletableFuture.failedFuture(new CancellationException());
              }
              // Use the same state snapshot for stage 2
              return runStage2ForwardBodiesAndReceipts(currentState);
            });
  }

  /**
   * Handles error from a download cycle. Updates state and decides whether to retry.
   *
   * @param error the error that occurred
   * @param overallResult the future to complete/continue
   */
  private void handleDownloadError(
      final Throwable error, final CompletableFuture<Void> overallResult) {

    // Update chain state to current blockchain head
    chainSyncState.updateAndGet(
        state -> state.fromHead(protocolContext.getBlockchain().getChainHeadHeader()));
    chainSyncStateStorage.storeState(chainSyncState.get());

    if (shouldRetry(error)) {
      LOG.warn("Chain sync encountered error, will retry from saved state", error);

      // Schedule next attempt without recursion
      // Use a small delay to avoid tight retry loops
      ethContext
          .getScheduler()
          .scheduleFutureTask(() -> attemptDownload(overallResult), Duration.ofMillis(100));
    } else {
      // Non-retryable error - fail (metrics will be stopped by outer handler)
      overallResult.completeExceptionally(error);
    }
  }

  /**
   * Handles the pivot update check loop without recursion. Uses explicit iteration via
   * CompletableFuture chaining.
   *
   * @param overallResult the future to complete when done
   */
  private void handlePivotUpdateLoop(final CompletableFuture<Void> overallResult) {
    if (overallResult.isDone()) {
      return;
    }

    checkForPivotUpdate()
        .whenComplete(
            (needsContinue, error) -> {
              if (error != null) {
                overallResult.completeExceptionally(error);
              } else if (needsContinue) {
                // Pivot updated, need another download cycle
                LOG.debug("Pivot updated, scheduling next download cycle");
                attemptDownload(overallResult);
              } else {
                // All done - world state heal finished, no more pivot updates
                // Metrics will be stopped by outer handler
                overallResult.complete(null);
              }
            });
  }

  /**
   * Checks for pivot updates and updates state if needed. Returns a future that completes with true
   * if a pivot update occurred (requiring another download cycle), or false if done.
   *
   * @return CompletableFuture<Boolean> - true if should continue, false if complete
   */
  private CompletableFuture<Boolean> checkForPivotUpdate() {
    final BlockHeader updatedPivot = pendingPivotUpdate.getAndSet(null);
    final BlockHeader previousPivot = chainSyncState.get().pivotBlockHeader();

    // Check if there's an immediate pivot update available
    if (pivotUpdateFuture.isDone()) {
      pivotUpdateFuture = new CompletableFuture<>();

      if (updatedPivot != null && updatedPivot.getNumber() > previousPivot.getNumber()) {
        LOG.debug(
            "Pivot block has been updated from {} to {}. Continuing sync to new pivot.",
            previousPivot.getNumber(),
            updatedPivot.getNumber());

        // Update chain state to new pivot
        chainSyncState.updateAndGet(state -> state.continueToNewPivot(updatedPivot, previousPivot));
        chainSyncStateStorage.storeState(chainSyncState.get());

        return CompletableFuture.completedFuture(true); // Need to continue
      }
    }

    LOG.debug(
        "No immediate pivot update detected. Waiting for world state heal to finish or pivot update ...");

    // Wait for either a pivot update or world state heal to complete
    return CompletableFuture.anyOf(pivotUpdateFuture, worldStateHealFinishedFuture)
        .thenApply(
            ignore -> {
              if (pivotUpdateFuture.isDone()) {
                // Recursive check - a pivot update arrived while waiting
                // We need to check it on the next iteration
                return true; // Need to continue with another cycle
              } else {
                // World state heal finished
                LOG.debug(
                    "World state heal finished (current pivot number: {}). Chain download complete.",
                    previousPivot.getNumber());
                return false; // All done
              }
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

  private static BlockHeader getCheckpointBlockHeader(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Hash checkpointHash) {
    // try the blockchain first, if not successful, download the header from the peers
    return protocolContext
        .getBlockchain()
        .getBlockHeader(checkpointHash)
        .orElse(downloadCheckpointHeader(protocolSchedule, ethContext, checkpointHash));
  }
}
