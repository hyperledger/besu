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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncTarget;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.GetHeadersFromPeerByHashTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.WaitForPeersTask;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChainDownloader<C> {
  private static final Logger LOG = LogManager.getLogger();

  private final SynchronizerConfiguration config;
  private final ProtocolContext<C> protocolContext;
  private final EthContext ethContext;
  private final SyncState syncState;
  private final SyncTargetManager<C> syncTargetManager;
  private final BlockImportTaskFactory blockImportTaskFactory;
  private final ProtocolSchedule<C> protocolSchedule;
  private final LabelledMetric<OperationTimer> ethTasksTimer;

  private final Deque<BlockHeader> checkpointHeaders = new ConcurrentLinkedDeque<>();
  private int checkpointTimeouts = 0;
  private int chainSegmentTimeouts = 0;

  private final AtomicBoolean started = new AtomicBoolean(false);
  private CompletableFuture<?> currentTask;

  public ChainDownloader(
      final SynchronizerConfiguration config,
      final ProtocolSchedule<C> protocolSchedule,
      final ProtocolContext<C> protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final LabelledMetric<OperationTimer> ethTasksTimer,
      final SyncTargetManager<C> syncTargetManager,
      final BlockImportTaskFactory blockImportTaskFactory) {
    this.protocolSchedule = protocolSchedule;
    this.ethTasksTimer = ethTasksTimer;
    this.config = config;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;

    this.syncState = syncState;
    this.syncTargetManager = syncTargetManager;
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
            .thenCompose(r -> importBlocks())
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
        || checkpointsHaveTimedOut()
        || chainSegmentsHaveTimedOut();
  }

  private boolean checkpointsHaveTimedOut() {
    // We have no more checkpoints, and have been unable to pull any new checkpoints for
    // several cycles.
    return checkpointHeaders.size() == 0
        && checkpointTimeouts >= config.downloaderCheckpointTimeoutsPermitted();
  }

  private boolean chainSegmentsHaveTimedOut() {
    return chainSegmentTimeouts >= config.downloaderChainSegmentTimeoutsPermitted();
  }

  private void clearSyncTarget() {
    syncState.syncTarget().ifPresent(this::clearSyncTarget);
  }

  private void clearSyncTarget(final SyncTarget syncTarget) {
    chainSegmentTimeouts = 0;
    checkpointTimeouts = 0;
    checkpointHeaders.clear();
    syncTargetManager.clearSyncTarget(syncTarget);
    syncState.clearSyncTarget();
  }

  private boolean shouldDownloadMoreCheckpoints() {
    return !syncTargetManager.isSyncTargetDisconnected()
        && checkpointHeaders.size() < config.downloaderHeaderRequestSize()
        && checkpointTimeouts < config.downloaderCheckpointTimeoutsPermitted();
  }

  private CompletableFuture<?> pullCheckpointHeaders(final SyncTarget syncTarget) {
    if (!shouldDownloadMoreCheckpoints()) {
      return CompletableFuture.completedFuture(null);
    }

    final BlockHeader lastHeader =
        checkpointHeaders.size() > 0 ? checkpointHeaders.getLast() : syncTarget.commonAncestor();
    // Try to pull more checkpoint headers
    return checkpointHeadersTask(lastHeader, syncTarget)
        .run()
        .handle(
            (r, t) -> {
              t = ExceptionUtils.rootCause(t);
              if (t instanceof TimeoutException) {
                checkpointTimeouts++;
                return null;
              } else if (t != null) {
                return r;
              }
              final List<BlockHeader> headers = r.getResult();
              if (headers.size() > 0
                  && checkpointHeaders.size() > 0
                  && checkpointHeaders.getLast().equals(headers.get(0))) {
                // Don't push header that is already tracked
                headers.remove(0);
              }
              if (headers.isEmpty()) {
                checkpointTimeouts++;
              } else {
                checkpointTimeouts = 0;
                checkpointHeaders.addAll(headers);
                LOG.debug("Tracking {} checkpoint headers", checkpointHeaders.size());
              }
              return r;
            });
  }

  private EthTask<PeerTaskResult<List<BlockHeader>>> checkpointHeadersTask(
      final BlockHeader lastHeader, final SyncTarget syncTarget) {
    LOG.debug("Requesting checkpoint headers from {}", lastHeader.getNumber());
    return GetHeadersFromPeerByHashTask.startingAtHash(
            protocolSchedule,
            ethContext,
            lastHeader.getHash(),
            lastHeader.getNumber(),
            config.downloaderHeaderRequestSize() + 1,
            config.downloaderChainSegmentSize() - 1,
            ethTasksTimer)
        .assignPeer(syncTarget.peer());
  }

  private CompletableFuture<List<Block>> importBlocks() {
    if (checkpointHeaders.isEmpty()) {
      // No checkpoints to download
      return CompletableFuture.completedFuture(Collections.emptyList());
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
            if (clearImportedCheckpointHeaders()) {
              chainSegmentTimeouts = 0;
            }
            if (t instanceof TimeoutException || r != null) {
              // Download timed out, or returned no new blocks
              chainSegmentTimeouts++;
            }
          } else {
            chainSegmentTimeouts = 0;
            final BlockHeader lastImportedCheckpoint = checkpointHeaders.getLast();
            checkpointHeaders.clear();
            syncState.setCommonAncestor(lastImportedCheckpoint);
          }
        });
  }

  private boolean clearImportedCheckpointHeaders() {
    final Blockchain blockchain = protocolContext.getBlockchain();
    // Update checkpoint headers to reflect if any checkpoints were imported.
    final List<BlockHeader> imported = new ArrayList<>();
    while (!checkpointHeaders.isEmpty()
        && blockchain.contains(checkpointHeaders.peekFirst().getHash())) {
      imported.add(checkpointHeaders.removeFirst());
    }
    final BlockHeader lastImportedCheckpointHeader = imported.get(imported.size() - 1);
    // The first checkpoint header is always present in the blockchain.
    checkpointHeaders.addFirst(lastImportedCheckpointHeader);
    syncState.setCommonAncestor(lastImportedCheckpointHeader);
    return imported.size() > 1;
  }

  public interface BlockImportTaskFactory {
    CompletableFuture<List<Block>> importBlocksForCheckpoints(
        final Deque<BlockHeader> checkpointHeaders);
  }
}
