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

import static java.util.Collections.emptyList;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import tech.pegasys.pantheon.ethereum.eth.manager.task.WaitForPeersTask;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthTaskChainDownloader<C> implements ChainDownloader {
  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration config;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final SyncTargetManager<C> syncTargetManager;
  private final CheckpointHeaderManager<C> checkpointHeaderManager;
  private final BlockImportTaskFactory blockImportTaskFactory;
  private final MetricsSystem metricsSystem;
  private final CompletableFuture<Void> downloadFuture = new CompletableFuture<>();

  private int chainSegmentTimeouts = 0;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private CompletableFuture<?> currentTask;

  public EthTaskChainDownloader(
      final SynchronizerConfiguration config,
      final EthContext ethContext,
      final SyncState syncState,
      final SyncTargetManager<C> syncTargetManager,
      final CheckpointHeaderManager<C> checkpointHeaderManager,
      final BlockImportTaskFactory blockImportTaskFactory,
      final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    this.config = config;
    this.ethContext = ethContext;

    this.syncState = syncState;
    this.syncTargetManager = syncTargetManager;
    this.checkpointHeaderManager = checkpointHeaderManager;
    this.blockImportTaskFactory = blockImportTaskFactory;
  }

  @Override
  public CompletableFuture<Void> start() {
    if (started.compareAndSet(false, true)) {
      executeDownload();
      return downloadFuture;
    } else {
      throw new IllegalStateException(
          "Attempt to start an already started " + this.getClass().getSimpleName() + ".");
    }
  }

  @Override
  public void cancel() {
    downloadFuture.cancel(true);
  }

  @VisibleForTesting
  public CompletableFuture<?> getCurrentTask() {
    return currentTask;
  }

  private void executeDownload() {
    if (downloadFuture.isDone()) {
      return;
    }
    // Find target, pull checkpoint headers, import, repeat
    currentTask =
        waitForPeers()
            .thenCompose(r -> syncTargetManager.findSyncTarget(syncState.syncTarget()))
            .thenApply(this::updateSyncState)
            .thenCompose(this::pullCheckpointHeaders)
            .thenCompose(this::importBlocks)
            .thenCompose(r -> checkSyncTarget())
            .whenComplete(
                (r, t) -> {
                  if (t != null) {
                    final Throwable rootCause = ExceptionUtils.rootCause(t);
                    if (rootCause instanceof CancellationException) {
                      LOG.trace("Download cancelled", t);
                    } else if (rootCause instanceof InvalidBlockException) {
                      LOG.debug("Invalid block downloaded", t);
                    } else if (rootCause instanceof EthTaskException) {
                      LOG.debug(rootCause.toString());
                    } else if (rootCause instanceof InterruptedException) {
                      LOG.trace("Interrupted while downloading chain", rootCause);
                    } else {
                      LOG.error("Error encountered while downloading", t);
                    }
                    // On error, wait a bit before retrying
                    ethContext
                        .getScheduler()
                        .scheduleFutureTask(this::executeDownload, Duration.ofSeconds(2));
                  } else if (syncTargetManager.shouldContinueDownloading()) {
                    executeDownload();
                  } else {
                    LOG.info("Chain download complete");
                    downloadFuture.complete(null);
                  }
                });
  }

  private SyncTarget updateSyncState(final SyncTarget newTarget) {
    if (isSameAsCurrentTarget(newTarget)) {
      return syncState.syncTarget().get();
    }
    return syncState.setSyncTarget(newTarget.peer(), newTarget.commonAncestor());
  }

  private Boolean isSameAsCurrentTarget(final SyncTarget newTarget) {
    return syncState
        .syncTarget()
        .map(currentTarget -> currentTarget.equals(newTarget))
        .orElse(false);
  }

  private CompletableFuture<List<BlockHeader>> pullCheckpointHeaders(final SyncTarget syncTarget) {
    return syncTarget.peer().isDisconnected()
        ? CompletableFuture.completedFuture(emptyList())
        : checkpointHeaderManager.pullCheckpointHeaders(syncTarget);
  }

  private CompletableFuture<?> waitForPeers() {
    return WaitForPeersTask.create(ethContext, 1, metricsSystem).run();
  }

  private CompletableFuture<Void> checkSyncTarget() {
    final Optional<SyncTarget> maybeSyncTarget = syncState.syncTarget();
    if (!maybeSyncTarget.isPresent()) {
      // No sync target, so nothing to check.
      return CompletableFuture.completedFuture(null);
    }

    final SyncTarget syncTarget = maybeSyncTarget.get();
    if (syncTargetManager.shouldSwitchSyncTarget(syncTarget)) {
      LOG.info("Better sync target found, clear current sync target: {}.", syncTarget);
      clearSyncTarget(syncTarget);
      return CompletableFuture.completedFuture(null);
    }
    if (finishedSyncingToCurrentTarget(syncTarget)) {
      LOG.info("Finished syncing to target: {}.", syncTarget);
      clearSyncTarget(syncTarget);
      // Wait a bit before checking for a new sync target
      final CompletableFuture<Void> future = new CompletableFuture<>();
      ethContext
          .getScheduler()
          .scheduleFutureTask(() -> future.complete(null), Duration.ofSeconds(10));
      return future;
    }
    return CompletableFuture.completedFuture(null);
  }

  private boolean finishedSyncingToCurrentTarget(final SyncTarget syncTarget) {
    return !syncTargetManager.syncTargetCanProvideMoreBlocks(syncTarget)
        || checkpointHeaderManager.checkpointsHaveTimedOut()
        || chainSegmentsHaveTimedOut();
  }

  private boolean chainSegmentsHaveTimedOut() {
    return chainSegmentTimeouts >= config.downloaderChainSegmentTimeoutsPermitted();
  }

  private void clearSyncTarget() {
    syncState.syncTarget().ifPresent(this::clearSyncTarget);
  }

  private void clearSyncTarget(final SyncTarget syncTarget) {
    chainSegmentTimeouts = 0;
    checkpointHeaderManager.clearSyncTarget();
    syncState.clearSyncTarget();
  }

  private CompletableFuture<List<Hash>> importBlocks(final List<BlockHeader> checkpointHeaders) {
    if (checkpointHeaders.isEmpty()) {
      // No checkpoints to download
      return CompletableFuture.completedFuture(emptyList());
    }

    final CompletableFuture<List<Hash>> importedBlocks =
        blockImportTaskFactory.importBlocksForCheckpoints(checkpointHeaders);

    return importedBlocks.whenComplete(
        (r, t) -> {
          t = ExceptionUtils.rootCause(t);
          if (t instanceof InvalidBlockException) {
            // Blocks were invalid, meaning our checkpoints are wrong
            // Reset sync target
            final Optional<SyncTarget> maybeSyncTarget = syncState.syncTarget();
            maybeSyncTarget.ifPresent(
                target -> target.peer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL));
            final String peerDescriptor =
                maybeSyncTarget
                    .map(SyncTarget::peer)
                    .map(EthPeer::toString)
                    .orElse("(unknown - already disconnected)");
            LOG.warn(
                "Invalid block discovered while downloading from peer {}.  Disconnect.",
                peerDescriptor);
            clearSyncTarget();
          } else if (t != null || r.isEmpty()) {
            if (t != null) {
              final Throwable rootCause = ExceptionUtils.rootCause(t);
              if (rootCause instanceof EthTaskException) {
                LOG.debug(rootCause.toString());
              } else if (rootCause instanceof InterruptedException) {
                LOG.trace("Interrupted while importing blocks", rootCause);
              } else {
                LOG.error("Encountered error importing blocks", t);
              }
            }
            if (checkpointHeaderManager.clearImportedCheckpointHeaders()) {
              chainSegmentTimeouts = 0;
            }
            if (t instanceof TimeoutException || r != null) {
              // Download timed out, or returned no new blocks
              chainSegmentTimeouts++;
            }
          } else {
            chainSegmentTimeouts = 0;

            final BlockHeader lastImportedCheckpoint =
                checkpointHeaderManager.allCheckpointsImported();
            syncState.setCommonAncestor(lastImportedCheckpoint);
          }
        });
  }

  public interface BlockImportTaskFactory {
    CompletableFuture<List<Hash>> importBlocksForCheckpoints(
        final List<BlockHeader> checkpointHeaders);
  }
}
