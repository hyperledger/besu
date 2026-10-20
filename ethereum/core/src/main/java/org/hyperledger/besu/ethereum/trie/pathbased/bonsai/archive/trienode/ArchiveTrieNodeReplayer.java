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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE_ARCHIVE;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background component that keeps the trie-node archive advancing after initial sync.
 *
 * <p>Trails a finalized target ({@code finalized} block, or {@code head − maxLayersToLoad} for
 * chains without finality). Per catch-up: takes a mutable layer over canonical head, rolls it back
 * to the cursor, then rolls forward one block at a time. Each block's {@code persist()} recomputes
 * the trie and emits {@code putFlat*TrieNode} writes that the archive capture strategy records via
 * a canonical archive transaction. Base (layer) writes stay in the discarded layer, never reaching
 * canonical. Because the target is always ≤ the finality/retention horizon, the capture path never
 * observes a reorg.
 */
public class ArchiveTrieNodeReplayer implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(ArchiveTrieNodeReplayer.class);

  private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000L;
  private static final long MAX_RETRY_DELAY_MILLIS = 60_000L;
  private static final long CLOSE_TIMEOUT_SECONDS = 30L;

  static final byte[] ARCHIVE_TRIE_NODE_SYNC_PROGRESS =
      "ARCHIVE_TRIE_NODE_SYNC_PROGRESS".getBytes(StandardCharsets.UTF_8);

  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final BonsaiWorldStateProvider worldStateProvider;
  private final TrieLogManager trieLogManager;
  private final Blockchain blockchain;
  private final ScheduledExecutorService executorService;
  private final ExecutorService trieCapturePool;
  private final int shallowCheckpointInterval;
  private final int deepCheckpointInterval;

  final AtomicLong cursor = new AtomicLong(0L);
  final AtomicLong replayTarget = new AtomicLong(0L);
  final AtomicBoolean catchUpRunning = new AtomicBoolean(false);
  volatile OptionalLong blockObserverId = OptionalLong.empty();
  private volatile boolean closed = false;
  // Set when the archive can no longer be extended without a gap; see RetentionGapException.
  volatile boolean halted = false;

  // Package-private so unit tests can shorten the retry backoff.
  volatile long initialRetryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
  volatile long maxRetryDelayMillis = MAX_RETRY_DELAY_MILLIS;
  // 0 means no failure in a row; only touched from the catch-up thread.
  private long retryDelayMillis = 0L;

  // Stateful rolling context — kept alive between catch-up runs to avoid re-positioning from HEAD.
  // Null means not yet initialised or invalidated after an error. Always accessed from the single
  // catch-up executor thread.
  // Package-private so unit tests can close the layer the way a flat-DB mode switch does.
  BonsaiWorldStateLayerStorage replayerLayer;
  private BonsaiSnapshotWorldStateKeyValueStorage replayerSnapshot;
  private BonsaiWorldState replayerState;
  private ArchiveTrieNodeStrategy replayerStrategy;
  // Package-private so unit tests can assert on positioning without going through the full stack.
  long replayerStatePosition = -1L;

  public ArchiveTrieNodeReplayer(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final BonsaiWorldStateProvider worldStateProvider,
      final TrieLogManager trieLogManager,
      final Blockchain blockchain,
      final ScheduledExecutorService executorService,
      final ExecutorService trieCapturePool,
      final int shallowCheckpointInterval,
      final int deepCheckpointInterval) {
    this.worldStateStorage = worldStateStorage;
    this.worldStateProvider = worldStateProvider;
    this.trieLogManager = trieLogManager;
    this.blockchain = blockchain;
    this.executorService = executorService;
    this.trieCapturePool = trieCapturePool;
    this.shallowCheckpointInterval = shallowCheckpointInterval;
    this.deepCheckpointInterval = deepCheckpointInterval;
  }

  /**
   * Seeds the cursor from the Phase-A handoff, registers a block observer, and schedules an initial
   * catch-up.
   */
  public void start(final long initialCursor) {
    if (closed) {
      return;
    }
    final long persisted = loadPersistedCursor().orElse(0L);
    cursor.set(Math.max(initialCursor, persisted));
    replayTarget.set(computeReplayTarget());
    LOG.info(
        "Archive trie-node replayer starting: cursor={} target={}",
        cursor.get(),
        replayTarget.get());

    blockObserverId =
        OptionalLong.of(
            blockchain.observeBlockAdded(
                event -> {
                  if (event.isNewCanonicalHead()) {
                    replayTarget.accumulateAndGet(computeReplayTarget(), Math::max);
                    scheduleCatchUpIfNeeded();
                  }
                }));
    scheduleCatchUpIfNeeded();
  }

  /**
   * The block number the replayer trails: the finalized block if a finality signal exists (QBFT
   * instant finality, PoS ~2 epochs behind head), otherwise {@code head - maxLayersToLoad / 2} for
   * chains without finality (Clique/PoW). Every block at or below it is beyond any possible reorg,
   * so the replayer never observes a competing fork.
   *
   * <p>A cold replayer positions at {@code target - 1} by rolling back from the head, which needs
   * trie logs for every block in between and so only works while {@code head - (target - 1)} is
   * within {@code maxLayersToLoad}. Trailing by half the retention window leaves the other half as
   * slack for the head to advance between catch-ups and rebuilds; trailing by the full window would
   * leave no slack at all, and a rebuild even two blocks late would lose its position.
   */
  @VisibleForTesting
  long computeReplayTarget() {
    return blockchain
        .getFinalized()
        .flatMap(blockchain::getBlockHeader)
        .map(BlockHeader::getNumber)
        .orElseGet(
            () ->
                Math.max(
                    0L,
                    blockchain.getChainHeadBlockNumber()
                        - trieLogManager.getMaxLayersToLoad() / 2));
  }

  private void scheduleCatchUpIfNeeded() {
    if (closed || halted || !catchUpRunning.compareAndSet(false, true)) {
      return;
    }
    try {
      executorService.submit(this::catchUp);
    } catch (final RejectedExecutionException e) {
      catchUpRunning.set(false);
      LOG.debug(
          "Trie-node replayer executor shut down; skipping catch-up to {}", replayTarget.get());
    }
  }

  private void catchUp() {
    final long from = cursor.get();
    final long target = replayTarget.get();
    boolean failed = false;
    try {
      captureRange(from, target);
      retryDelayMillis = 0L;
    } catch (final RetentionGapException e) {
      halted = true;
      LOG.error(e.getMessage());
    } catch (final Exception e) {
      failed = true;
      LOG.error(
          "Archive trie-node replayer: error capturing range ({}, {}]; cursor stays at {}",
          from,
          target,
          cursor.get(),
          e);
    } finally {
      final long reached = cursor.get();
      if (reached > from && reached >= target) {
        LOG.info("Archive trie-node replayer: fully caught up at block {}", reached);
      } else if (reached > from) {
        LOG.debug("Archive trie-node replayer: advanced to {} (target {})", reached, target);
      }
      if (failed && !closed) {
        // Keep catchUpRunning set while backing off so head events cannot bypass the delay.
        scheduleRetry();
      } else {
        catchUpRunning.set(false);
        if (!closed && reached < replayTarget.get()) {
          scheduleCatchUpIfNeeded();
        }
      }
    }
  }

  private void scheduleRetry() {
    final long delay = retryDelayMillis > 0 ? retryDelayMillis : initialRetryDelayMillis;
    retryDelayMillis = Math.min(delay * 2, maxRetryDelayMillis);
    LOG.debug("Archive trie-node replayer: retrying in {} ms", delay);
    try {
      executorService.schedule(this::catchUp, delay, TimeUnit.MILLISECONDS);
    } catch (final RejectedExecutionException e) {
      catchUpRunning.set(false);
      LOG.debug("Trie-node replayer executor shut down; skipping retry");
    }
  }

  /**
   * Captures archive-history entries for all blocks in {@code (fromExclusive, toInclusive]}.
   *
   * <p>On the first call (or after an error), creates a fresh layer over canonical head, rolls it
   * back to {@code fromExclusive} via {@link #positionAtCursor}, then rolls forward. On subsequent
   * calls the layer and state are kept alive between runs — no re-positioning needed — so the cost
   * is proportional to the number of blocks captured rather than the size of the trie-log window.
   *
   * <p>On exception the stateful context is torn down so the next call starts clean.
   */
  @VisibleForTesting
  void captureRange(final long fromExclusive, final long toInclusive) {
    if (toInclusive <= fromExclusive) {
      return;
    }
    boolean ok = false;
    SegmentedKeyValueStorageTransaction canonicalTx = null;
    try {
      ensureReplayerState(fromExclusive);

      // Re-read cursor: ensureReplayerState may have advanced it past fromExclusive when the
      // retention window was exceeded. The loop start reflects the actual position of
      // replayerState.
      final long effectiveFrom = cursor.get();
      if (toInclusive <= effectiveFrom) {
        ok = true;
        return;
      }

      final ArchiveTrieNodeWriter writer = replayerStrategy.getTrieNodeWriter();

      for (long b = effectiveFrom + 1; b <= toInclusive && !closed; b++) {
        final BlockHeader header = blockchain.getBlockHeader(b).orElseThrow();
        final TrieLog log = trieLogManager.getTrieLogLayer(header.getHash()).orElseThrow();

        canonicalTx =
            worldStateStorage.getComposedWorldStateStorage().startLowPriorityTransaction();
        writer.setArchiveWriteTransaction(canonicalTx);

        final PathBasedWorldStateUpdateAccumulator<?> acc =
            (PathBasedWorldStateUpdateAccumulator<?>) replayerState.getAccumulator();
        acc.rollForward(log);
        acc.commit();
        replayerState.persist(
            header); // recompute → emits node writes → onBeforeCommit → canonicalTx

        canonicalTx.put(
            TRIE_BRANCH_STORAGE_ARCHIVE,
            ARCHIVE_TRIE_NODE_SYNC_PROGRESS,
            Bytes.ofUnsignedLong(b).toArrayUnsafe());
        canonicalTx.commit(); // archive history + coverage + cursor, atomically
        canonicalTx = null;
        writer.setArchiveWriteTransaction(null);
        cursor.set(b);
        replayerStatePosition = b;
        if (b % 1000 == 0) {
          LOG.debug(
              "Archive trie-node replayer: block {} / {} ({} remaining)",
              b,
              toInclusive,
              toInclusive - b);
        }
      }
      ok = true;
    } finally {
      if (!ok) {
        rollbackQuietly(canonicalTx);
        tearDownReplayerState();
      }
    }
  }

  /**
   * Ensures {@link #replayerLayer}, {@link #replayerState}, and {@link #replayerStrategy} are
   * initialised and positioned at {@code fromExclusive}. Reuses the existing context when the state
   * is already there; otherwise tears down any stale context and re-positions from HEAD.
   */
  private void ensureReplayerState(final long fromExclusive) {
    if (replayerState != null && replayerLayer.isClosed()) {
      // The layer subscribes to the canonical storage and closes itself when that is notified of
      // a flat-DB mode change (the archive migration finishing). Rebuild it at the same cursor.
      LOG.info("Archive trie-node replayer layer storage was closed; rebuilding replayer state");
      tearDownReplayerState();
    }
    if (replayerState != null && replayerStatePosition == fromExclusive) {
      // State is already positioned at fromExclusive from the previous run — nothing to do.
      return;
    }
    if (replayerState != null) {
      LOG.warn(
          "Archive trie-node replayer state is at {} but fromExclusive={}; re-positioning",
          replayerStatePosition,
          fromExclusive);
      tearDownReplayerState();
    }

    replayerSnapshot = new BonsaiSnapshotWorldStateKeyValueStorage(worldStateStorage);
    final long head =
        replayerSnapshot
            .getWorldStateBlockNumber()
            .orElseThrow(
                () ->
                    new IllegalStateException("Replayer snapshot has no world state block number"));

    // Guard: if the cursor is older than the trie-log retention window, we cannot roll back
    // far enough to reposition. Advance cursor to the oldest reachable block and position the
    // state there. The current captureRange call will archive nothing (toInclusive == cursor),
    // but the state is ready for the next call to roll forward from.
    final long maxLayers = trieLogManager.getMaxLayersToLoad();
    final long oldestReachable = Math.max(0L, head - maxLayers);
    final long positionTarget;
    if (fromExclusive < oldestReachable) {
      if (new ArchiveCoverageTracker(worldStateStorage.getComposedWorldStateStorage())
          .hasCoverage()) {
        // The archive already covers blocks up to the cursor. Skipping ahead would leave blocks
        // without history entries inside the covered window, and proofs for them would silently
        // read stale nodes. Stop instead and leave the coverage at its last contiguous block.
        throw new RetentionGapException(
            String.format(
                "Archive trie-node replayer cannot continue: cursor %d is beyond trie-log retention"
                    + " (head=%d, maxLayers=%d), so blocks %d-%d cannot be archived. Archive proof"
                    + " coverage stays at block %d and the replayer is stopping; the archive must be"
                    + " resynced to extend it.",
                fromExclusive, head, maxLayers, fromExclusive + 1, oldestReachable, fromExclusive));
      }
      // Nothing archived yet (proofs just enabled): start from the oldest reachable block.
      LOG.warn(
          "Archive trie-node replayer cursor {} is beyond trie-log retention (head={}, maxLayers={})."
              + " Advancing cursor to {} — blocks {}-{} will not be archived.",
          fromExclusive,
          head,
          maxLayers,
          oldestReachable,
          fromExclusive + 1,
          oldestReachable);
      cursor.set(oldestReachable);
      positionTarget = oldestReachable;
      // Fall through: position replayerState at oldestReachable so the next catch-up can roll
      // forward from there without re-positioning.
    } else {
      positionTarget = fromExclusive;
    }

    replayerStrategy =
        ArchiveTrieNodeStrategy.createReplayerStrategy(
            worldStateStorage.getComposedWorldStateStorage(),
            trieCapturePool,
            shallowCheckpointInterval,
            deepCheckpointInterval);
    replayerLayer = new BonsaiWorldStateLayerStorage(replayerSnapshot);
    replayerLayer.setTrieNodeStrategy(replayerStrategy);
    replayerState = worldStateProvider.newTrieEnabledWorldState(replayerLayer);
    positionAtCursor(replayerState, positionTarget, head);
    replayerStatePosition = positionTarget;
  }

  /** The cursor is older than trie-log retention while the archive already has coverage. */
  static final class RetentionGapException extends IllegalStateException {
    RetentionGapException(final String message) {
      super(message);
    }
  }

  private void rollbackQuietly(final SegmentedKeyValueStorageTransaction tx) {
    if (tx == null) {
      return;
    }
    try {
      tx.rollback();
    } catch (final Exception e) {
      LOG.warn("Failed to roll back replayer archive transaction", e);
    }
  }

  private void tearDownReplayerState() {
    replayerState = null;
    replayerStrategy = null;
    replayerStatePosition = -1L;
    if (replayerLayer != null) {
      try {
        // Closing an already-closed layer logs an "access closed worldstate" warning.
        if (!replayerLayer.isClosed()) {
          replayerLayer.close();
        }
      } catch (final Exception e) {
        LOG.warn("Failed to close replayer layer storage", e);
      }
      replayerLayer = null;
    }
    if (replayerSnapshot != null) {
      try {
        // Releases the pinned storage snapshot.
        if (!replayerSnapshot.isClosed()) {
          replayerSnapshot.close();
        }
      } catch (final Exception e) {
        LOG.warn("Failed to close replayer snapshot storage", e);
      }
      replayerSnapshot = null;
    }
  }

  private void positionAtCursor(
      final BonsaiWorldState state, final long targetBlock, final long head) {
    final long rollbackDepth = head - targetBlock;
    final long maxLayers = trieLogManager.getMaxLayersToLoad();
    if (rollbackDepth > maxLayers) {
      throw new IllegalStateException(
          String.format(
              "Cannot position replayer at block %d: rollback depth %d exceeds trie-log retention %d (head=%d). "
                  + "Cursor is too far behind head to reposition via trie-log rollback.",
              targetBlock, rollbackDepth, maxLayers, head));
    }
    final PathBasedWorldStateUpdateAccumulator<?> acc =
        (PathBasedWorldStateUpdateAccumulator<?>) state.getAccumulator();
    for (long b = head; b > targetBlock; b--) {
      final BlockHeader header = blockchain.getBlockHeader(b).orElseThrow();
      final long blockNum = b;
      final TrieLog log =
          trieLogManager
              .getTrieLogLayer(header.getHash())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Trie-log missing for block "
                              + blockNum
                              + " while positioning replayer at "
                              + targetBlock));
      acc.rollBack(log);
    }
    acc.commit();
    // No archive redirect during positioning (override is null): any node writes go to the
    // discarded layer transaction, not to canonical.
    state.persist(blockchain.getBlockHeader(targetBlock).orElseThrow());
  }

  @VisibleForTesting
  OptionalLong loadPersistedCursor() {
    return worldStateStorage
        .getComposedWorldStateStorage()
        .get(TRIE_BRANCH_STORAGE_ARCHIVE, ARCHIVE_TRIE_NODE_SYNC_PROGRESS)
        .map(b -> OptionalLong.of(Bytes.wrap(b).toLong()))
        .orElse(OptionalLong.empty());
  }

  public long getCursor() {
    return cursor.get();
  }

  @Override
  public void close() {
    closed = true;
    blockObserverId.ifPresent(blockchain::removeObserver);
    blockObserverId = OptionalLong.empty();
    executorService.shutdownNow();
    try {
      if (!executorService.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        LOG.warn(
            "Archive trie-node replayer catch-up did not stop within {}s of close",
            CLOSE_TIMEOUT_SECONDS);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // Only now is it safe to release the layer: the catch-up thread no longer uses it.
    tearDownReplayerState();
  }
}
