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

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.WaitForPeersTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
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

public class ChainDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration config;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final SyncTargetManager<C> syncTargetManager;
  private final CheckpointHeaderManager<C> checkpointHeaderManager;
  private final BlockImportTaskFactory blockImportTaskFactory;
  private final LabelledMetric<OperationTimer> ethTasksTimer;

  private int chainSegmentTimeouts = 0;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private CompletableFuture<?> currentTask;

  public ChainDownloader(
      final SynchronizerConfiguration config,
      final EthContext ethContext,
      final SyncState syncState,
      final LabelledMetric<OperationTimer> ethTasksTimer,
      final SyncTargetManager<C> syncTargetManager,
      final CheckpointHeaderManager<C> checkpointHeaderManager,
      final BlockImportTaskFactory blockImportTaskFactory) {
    this.ethTasksTimer = ethTasksTimer;
    this.config = config;
    this.ethContext = ethContext;

    this.syncState = syncState;
    this.syncTargetManager = syncTargetManager;
    this.checkpointHeaderManager = checkpointHeaderManager;
    this.blockImportTaskFactory = blockImportTaskFactory;
  }

  public void start() {
    if (started.compareAndSet(false, true)) {
      executeDownload();
    } else {
      throw new IllegalStateException(
          "Attempt to start an already started " + this.getClass().getSimpleName() + ".");
    }
  }

  @VisibleForTesting
  public CompletableFuture<?> getCurrentTask() {
    return currentTask;
  }

  private CompletableFuture<?> executeDownload() {
    // Find target, pull checkpoint headers, import, repeat
    currentTask =
        waitForPeers()
            .thenCompose(r -> syncTargetManager.findSyncTarget())
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
                    } else {
                      LOG.error("Error encountered while downloading", t);
                    }
                    // On error, wait a bit before retrying
                    ethContext
                        .getScheduler()
                        .scheduleFutureTask(this::executeDownload, Duration.ofSeconds(2));
                  } else {
                    executeDownload();
                  }
                });
    return currentTask;
  }

  private CompletableFuture<List<BlockHeader>> pullCheckpointHeaders(final SyncTarget syncTarget) {
    return syncTargetManager.isSyncTargetDisconnected()
        ? CompletableFuture.completedFuture(emptyList())
        : checkpointHeaderManager.pullCheckpointHeaders(syncTarget);
  }

  private CompletableFuture<?> waitForPeers() {
    return WaitForPeersTask.create(ethContext, 1, ethTasksTimer).run();
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
    if (finishedSyncingToCurrentTarget()) {
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

  private boolean finishedSyncingToCurrentTarget() {
    return syncTargetManager.isSyncTargetDisconnected()
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
    syncTargetManager.clearSyncTarget(syncTarget);
    syncState.clearSyncTarget();
  }

  private CompletableFuture<List<Block>> importBlocks(final List<BlockHeader> checkpointHeaders) {
    if (checkpointHeaders.isEmpty()) {
      // No checkpoints to download
      return CompletableFuture.completedFuture(emptyList());
    }

    final CompletableFuture<List<Block>> importedBlocks =
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
              LOG.error("Encountered error importing blocks", t);
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
    CompletableFuture<List<Block>> importBlocksForCheckpoints(
        final List<BlockHeader> checkpointHeaders);
  }
}
