/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive;

import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.TrailingTrieNodeArchiver;
import org.hyperledger.besu.plugin.data.AddedBlockContext.EventType;

import java.io.Closeable;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the archive workers that trail the chain head: the flat-archive migrator and, when
 * present, the trailing trie-node archiver.
 *
 * <p>Owns the only block observer, which raises a shared target as new canonical heads arrive, and
 * one single-thread executor on which every worker step runs. A run steps each enabled worker that
 * is due towards the target, flat first, then reschedules itself while any worker is still due.
 * Each worker backs off on its own after a failure, so one failing worker does not stall the other.
 * Steps yield between blocks when the coordinator closes or a proofs catch-up is waiting.
 */
public final class BonsaiArchiveTrailingSync implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiveTrailingSync.class);

  /** How far the target stays above the oldest block the trie-log layers can still load. */
  static final long TRAILING_BUFFER_BLOCKS = 32L;

  private static final long MAX_PROOFS_BATCH_BLOCKS = 32L;
  private static final long DEFAULT_INITIAL_RETRY_DELAY_MILLIS = 1_000L;
  private static final long DEFAULT_MAX_RETRY_DELAY_MILLIS = 60_000L;
  private static final long CLOSE_TIMEOUT_SECONDS = 30L;

  private final Blockchain blockchain;
  private final long maxLayersToLoad;
  private final long proofsBatchBlocks;
  private final BonsaiFlatDbToArchiveMigrator migrator;
  private final Optional<TrailingTrieNodeArchiver> archiver;
  private final ScheduledExecutorService executorService;

  /** The highest block the workers should reach; only ever raised. */
  private final AtomicLong targetBlock = new AtomicLong(0L);

  /** Set while a run is queued or running, so only one is ever in flight. */
  private final AtomicBoolean runQueued = new AtomicBoolean(false);

  private final WorkerState flat = new WorkerState("Flat archive migrator");
  private final WorkerState proofs = new WorkerState("Trailing trie-node archiver");

  private volatile OptionalLong blockObserverId = OptionalLong.empty();
  private volatile boolean closed = false;
  private volatile boolean flatEnabled = false;
  private volatile boolean proofsEnabled = false;
  // Set while a proofs catch-up waits to start; running steps yield to it.
  private volatile boolean catchUpPending = false;
  private volatile long initialRetryDelayMillis = DEFAULT_INITIAL_RETRY_DELAY_MILLIS;
  private volatile long maxRetryDelayMillis = DEFAULT_MAX_RETRY_DELAY_MILLIS;
  // Earliest backoff wake-up already scheduled, Long.MAX_VALUE if none; executor thread only.
  private long wakeUpAtNanos = Long.MAX_VALUE;

  /**
   * Creates the coordinator. Nothing runs until {@link #start} and a worker is enabled.
   *
   * @param blockchain the blockchain whose new canonical heads advance the target
   * @param maxLayersToLoad how many trie-log layers back the world state can roll
   * @param migrator the flat-archive worker
   * @param archiver the trie-node archive worker, empty when proofs are not archived
   * @param executorService the single-threaded executor every step runs on; shut down by {@link
   *     #close}
   */
  public BonsaiArchiveTrailingSync(
      final Blockchain blockchain,
      final long maxLayersToLoad,
      final BonsaiFlatDbToArchiveMigrator migrator,
      final Optional<TrailingTrieNodeArchiver> archiver,
      final ScheduledExecutorService executorService) {
    this.blockchain = blockchain;
    this.maxLayersToLoad = maxLayersToLoad;
    // A proofs run rolls back from the head once, so batch blocks up to amortise that.
    this.proofsBatchBlocks = Math.clamp(maxLayersToLoad / 4, 1L, MAX_PROOFS_BATCH_BLOCKS);
    this.migrator = migrator;
    this.archiver = archiver;
    this.executorService = executorService;
  }

  /**
   * The target both workers trail to for a canonical head: just inside the trie-log window, so the
   * blocks they process can still be rolled to.
   *
   * @param head the canonical head's block number
   * @return the target block, never negative
   */
  @VisibleForTesting
  long sharedTarget(final long head) {
    return Math.max(0L, head - maxLayersToLoad + TRAILING_BUFFER_BLOCKS);
  }

  /** Starts following new canonical heads, scheduling a run whenever a worker becomes due. */
  public synchronized void start() {
    if (closed || blockObserverId.isPresent()) {
      return;
    }
    blockObserverId = OptionalLong.of(blockchain.observeBlockAdded(this::onBlockAdded));
  }

  private void onBlockAdded(final BlockAddedEvent event) {
    if (event.getEventType() == EventType.CHAIN_REORG) {
      haltWorkersHoldingReorgedBlocks(event);
    }
    if (event.isNewCanonicalHead()) {
      targetBlock.accumulateAndGet(sharedTarget(event.getHeader().getNumber()), Math::max);
      scheduleRunIfReady();
    }
  }

  /**
   * Halts each worker whose archive already holds blocks the reorg replaced: the workers only
   * trail, so this happens only when the reorg is deeper than the trailing buffer. Archived data
   * cannot be rewound, so the archive needs a resync.
   */
  private void haltWorkersHoldingReorgedBlocks(final BlockAddedEvent event) {
    final Optional<BlockHeader> ancestorHeader =
        blockchain.getBlockHeader(event.getCommonAncestorHash());
    if (ancestorHeader.isEmpty()) {
      LOG.warn(
          "Chain reorg common ancestor {} not found; cannot check the archive against it",
          event.getCommonAncestorHash());
      return;
    }
    final long ancestor = ancestorHeader.get().getNumber();
    if (ancestor < migrator.getMigratedBlockNumber()) {
      haltForReorg(flat, "flat", ancestor, migrator.getMigratedBlockNumber());
    }
    archiver
        .map(TrailingTrieNodeArchiver::coverageEnd)
        .filter(end -> end.isPresent() && ancestor < end.getAsLong())
        .ifPresent(end -> haltForReorg(proofs, "proofs", ancestor, end.getAsLong()));
  }

  private void haltForReorg(
      final WorkerState worker, final String workerName, final long ancestor, final long position) {
    worker.halted = true;
    LOG.error(
        "Chain reorg to common ancestor {} replaced blocks the {} archive already holds (up to {}); halting it. Resync the archive to recover.",
        ancestor,
        workerName,
        position);
  }

  /**
   * Starts the flat-archive worker, resuming after the last migrated block. Called once the flat
   * database is in archive mode.
   */
  public void startFlatArchive() {
    migrator.loadMigrationProgress();
    flatEnabled = true;
    scheduleRunIfReady();
  }

  /**
   * Enables or disables the trailing trie-node archiver; disabled while inline capture archives
   * proofs. Halt and backoff state survive a disable.
   *
   * @param enabled true to let runs step the proofs worker
   */
  public void setProofsArchiving(final boolean enabled) {
    proofsEnabled = enabled;
    if (enabled) {
      scheduleRunIfReady();
    }
  }

  /**
   * Archives proofs up to {@code block} on the coordinator's executor and waits for it. A running
   * step yields to it at its next block boundary. Runs whether or not proofs archiving is enabled.
   *
   * @param block the last block to archive
   * @return true if the archive now reaches the block; false if there is no archiver, the
   *     coordinator closed, the wait was interrupted or the catch-up threw
   */
  public boolean catchUpProofsTo(final long block) {
    if (archiver.isEmpty() || closed) {
      return false;
    }
    final TrailingTrieNodeArchiver trieNodeArchiver = archiver.get();
    catchUpPending = true;
    final Future<Boolean> catchUp;
    try {
      catchUp =
          executorService.submit(
              () -> {
                catchUpPending = false;
                try {
                  return trieNodeArchiver.catchUpTo(block, () -> !closed);
                } finally {
                  // A run that yielded to this catch-up left rescheduling to it.
                  rescheduleIfDue();
                }
              });
    } catch (final RejectedExecutionException e) {
      catchUpPending = false;
      return false;
    }
    try {
      return catchUp.get();
    } catch (final CancellationException e) {
      return false;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (final ExecutionException e) {
      LOG.warn("Trailing trie-node archive catch-up to block {} failed", block, e.getCause());
      return false;
    }
  }

  private boolean keepGoing() {
    return !closed && !catchUpPending;
  }

  private boolean flatDue(final long target) {
    return flatEnabled && migrator.getMigratedBlockNumber() < target;
  }

  private boolean proofsDue(final long target) {
    return proofsEnabled
        && archiver.isPresent()
        && target - archiver.get().getArchivedBlockNumber() >= proofsBatchBlocks;
  }

  private boolean anyReady(final long target, final long nowNanos) {
    return (flat.canStep(nowNanos) && flatDue(target))
        || (proofs.canStep(nowNanos) && proofsDue(target));
  }

  private void scheduleRunIfReady() {
    if (!closed && anyReady(targetBlock.get(), System.nanoTime())) {
      submitRun();
    }
  }

  private void submitRun() {
    if (closed || !runQueued.compareAndSet(false, true)) {
      return;
    }
    try {
      executorService.submit(this::run);
    } catch (final RejectedExecutionException e) {
      runQueued.set(false);
      LOG.debug("Archive trailing sync executor shut down; skipping run");
    }
  }

  private void run() {
    try {
      final long target = targetBlock.get();
      if (keepGoing() && flat.canStep(System.nanoTime()) && flatDue(target)) {
        step(
            flat,
            migrator::getMigratedBlockNumber,
            target,
            () -> migrator.migrateTo(target, this::keepGoing));
      }
      // Checked again so a proofs step does not roll back only to yield straight away.
      archiver.ifPresent(
          trieNodeArchiver -> {
            if (keepGoing() && proofs.canStep(System.nanoTime()) && proofsDue(target)) {
              step(
                  proofs,
                  trieNodeArchiver::getArchivedBlockNumber,
                  target,
                  () -> trieNodeArchiver.archiveTo(target, this::keepGoing));
            }
          });
    } finally {
      runQueued.set(false);
      // A pending catch-up reschedules once it has run, so the next run does not just yield again.
      if (!catchUpPending) {
        rescheduleIfDue();
      }
    }
  }

  private void step(
      final WorkerState worker,
      final LongSupplier position,
      final long target,
      final Runnable work) {
    try {
      work.run();
      worker.succeeded();
    } catch (final TrailingTrieNodeArchiver.ArchiveGapException e) {
      // Retrying cannot recreate a missing trie log; the archive needs a resync.
      worker.halted = true;
      LOG.error(e.getMessage());
    } catch (final RuntimeException e) {
      if (closed) {
        LOG.debug("{} step interrupted by close", worker.name, e);
        return;
      }
      final long delay = worker.failed(initialRetryDelayMillis, maxRetryDelayMillis);
      LOG.error(
          "{} failed at block {} (target {}); retrying in {} ms",
          worker.name,
          position.getAsLong(),
          target,
          delay,
          e);
    }
  }

  /**
   * Submits another run if a worker is ready, otherwise wakes up when the earliest backoff of a due
   * worker expires. Runs on the executor thread.
   */
  private void rescheduleIfDue() {
    if (closed) {
      return;
    }
    final long target = targetBlock.get();
    final long now = System.nanoTime();
    if (anyReady(target, now)) {
      submitRun();
      return;
    }
    long retryAt = Long.MAX_VALUE;
    if (flat.isBackingOff(now) && flatDue(target)) {
      retryAt = flat.retryAtNanos;
    }
    if (proofs.isBackingOff(now) && proofsDue(target) && proofs.retryAtNanos - retryAt < 0) {
      retryAt = proofs.retryAtNanos;
    }
    if (retryAt != Long.MAX_VALUE) {
      scheduleWakeUp(retryAt, now);
    }
  }

  private void scheduleWakeUp(final long atNanos, final long nowNanos) {
    if (wakeUpAtNanos != Long.MAX_VALUE && wakeUpAtNanos - atNanos <= 0) {
      return;
    }
    wakeUpAtNanos = atNanos;
    try {
      executorService.schedule(
          () -> {
            if (wakeUpAtNanos == atNanos) {
              wakeUpAtNanos = Long.MAX_VALUE;
            }
            submitRun();
          },
          atNanos - nowNanos,
          TimeUnit.NANOSECONDS);
    } catch (final RejectedExecutionException e) {
      wakeUpAtNanos = Long.MAX_VALUE;
      LOG.debug("Archive trailing sync executor shut down; skipping retry");
    }
  }

  /**
   * Stops following heads, cancels queued work (releasing callers waiting in {@link
   * #catchUpProofsTo}) and waits for the running step to stop at its next block boundary.
   */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    blockObserverId.ifPresent(blockchain::removeObserver);
    blockObserverId = OptionalLong.empty();
    // shutdownNow drops queued tasks without completing their futures; cancel them so waiting
    // callers are released instead of blocking forever.
    for (final Runnable dropped : executorService.shutdownNow()) {
      if (dropped instanceof Future<?> future) {
        future.cancel(false);
      }
    }
    try {
      if (!executorService.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        LOG.warn("Archive trailing sync did not stop within {}s of close", CLOSE_TIMEOUT_SECONDS);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Whether the flat-archive worker has stopped for good.
   *
   * @return true once halted
   */
  @VisibleForTesting
  boolean isFlatHalted() {
    return flat.halted;
  }

  /**
   * Whether the trie-node archive worker has stopped for good.
   *
   * @return true once halted
   */
  @VisibleForTesting
  boolean isProofsHalted() {
    return proofs.halted;
  }

  /**
   * Overrides the retry backoff so tests need not wait for the defaults.
   *
   * @param initialMillis delay before the first retry
   * @param maxMillis cap on the doubling delay
   */
  @VisibleForTesting
  void setRetryDelaysMillis(final long initialMillis, final long maxMillis) {
    this.initialRetryDelayMillis = initialMillis;
    this.maxRetryDelayMillis = maxMillis;
  }

  /**
   * Halt and backoff state of one worker. Written on the executor thread; read by the block
   * observer to decide whether a run is worth submitting.
   */
  private static final class WorkerState {
    private final String name;
    private volatile boolean halted = false;
    // 0 while the last step succeeded, otherwise the delay of the current backoff.
    private volatile long retryDelayMillis = 0L;
    private volatile long retryAtNanos = 0L;

    private WorkerState(final String name) {
      this.name = name;
    }

    private boolean isBackingOff(final long nowNanos) {
      return retryDelayMillis > 0 && nowNanos - retryAtNanos < 0;
    }

    private boolean canStep(final long nowNanos) {
      return !halted && !isBackingOff(nowNanos);
    }

    private void succeeded() {
      retryDelayMillis = 0L;
    }

    private long failed(final long initialMillis, final long maxMillis) {
      final long delay =
          retryDelayMillis > 0 ? Math.min(retryDelayMillis * 2, maxMillis) : initialMillis;
      retryAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay);
      retryDelayMillis = delay;
      return delay;
    }
  }
}
