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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.PathBasedWorldState;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.io.Closeable;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

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
  // Every run repositions a fresh state from the head, so runs triggered by new heads wait until
  // this many blocks are pending (capped relative to the retention window; see batchReady()).
  private static final long CATCH_UP_BATCH_BLOCKS = 32L;

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
  // Set when the archive can no longer be extended without a gap; see ArchiveGapException.
  volatile boolean halted = false;

  // Package-private so unit tests can shorten the retry backoff.
  volatile long initialRetryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
  volatile long maxRetryDelayMillis = MAX_RETRY_DELAY_MILLIS;
  // 0 means no failure in a row; only touched from the catch-up thread.
  private long retryDelayMillis = 0L;

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
   * Seeds the cursor from the archive's coverage record, registers a block observer, and schedules
   * an initial catch-up. The coverage record is written atomically with each archived block, by the
   * replayer or by the live capture before it, so it always says how far the archive reaches.
   */
  public void start() {
    if (closed) {
      return;
    }
    cursor.set(
        new ArchiveCoverageTracker(worldStateStorage.getComposedWorldStateStorage())
            .lastCoveredBlock()
            .orElse(0L));
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
                    if (batchReady()) {
                      scheduleCatchUpIfNeeded();
                    }
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

  /**
   * Whether enough blocks are pending to justify a run. Every run opens a fresh state and rolls it
   * back from the head to the cursor, so runs triggered by new heads wait for a batch instead of
   * rolling back once per block. The batch is capped at a quarter of the retention window: the
   * cursor trails the head by about half of it plus one batch, and the rollback must stay inside
   * the window.
   */
  private boolean batchReady() {
    final long batch =
        Math.max(1L, Math.min(CATCH_UP_BATCH_BLOCKS, trieLogManager.getMaxLayersToLoad() / 4));
    return replayTarget.get() - cursor.get() >= batch;
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
    } catch (final ArchiveGapException e) {
      halted = true;
      LOG.error(e.getMessage());
    } catch (final Exception e) {
      failed = true;
      if (closed) {
        LOG.debug("Archive trie-node replayer: catch-up interrupted by close", e);
      } else {
        LOG.error(
            "Archive trie-node replayer: error capturing range ({}, {}]; cursor stays at {}",
            from,
            target,
            cursor.get(),
            e);
      }
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
        if (!closed && batchReady()) {
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
   * <p>Each run opens its own layered state over a snapshot of the persisted head, rolls it back to
   * {@code fromExclusive}, then rolls it forward one block at a time. The state is closed when the
   * run ends, so the layer's accumulated writes and the snapshot it pins are released and nothing
   * outlives a run. Callers amortise the rollback by running in batches; see {@link #batchReady()}.
   */
  @VisibleForTesting
  void captureRange(final long fromExclusive, final long toInclusive) {
    if (toInclusive <= fromExclusive) {
      return;
    }
    final ArchiveTrieNodeStrategy strategy =
        ArchiveTrieNodeStrategy.createReplayerStrategy(
            worldStateStorage.getComposedWorldStateStorage(),
            trieCapturePool,
            shallowCheckpointInterval,
            deepCheckpointInterval);
    final PathBasedWorldState state = openPositionedState(fromExclusive, strategy);
    SegmentedKeyValueStorageTransaction canonicalTx = null;
    try {
      final ArchiveTrieNodeWriter writer = strategy.getTrieNodeWriter();

      // Re-read cursor: opening the state may have advanced it past fromExclusive when the
      // retention window was exceeded. The loop start reflects the actual position of the state.
      for (long b = cursor.get() + 1; b <= toInclusive && !closed; b++) {
        final Hash blockHash = canonicalHash(b);
        final long blockNumber = b;
        // A log that is missing for a block inside the target is pruned or was never stored, so
        // retrying cannot help: stop like any other gap rather than retry forever. Checked here
        // because rolling reports a missing log only as a generic rolling failure.
        if (trieLogManager.getTrieLogLayer(blockHash).isEmpty()) {
          throw new ArchiveGapException(
              String.format(
                  "Archive trie-node replayer cannot continue: the trie log for block %d"
                      + " is missing, so it cannot be archived. Archive proof coverage"
                      + " stays at block %d and the replayer is stopping; the archive"
                      + " must be resynced to extend it.",
                  blockNumber, blockNumber - 1));
        }

        canonicalTx =
            worldStateStorage.getComposedWorldStateStorage().startLowPriorityTransaction();
        writer.setArchiveWriteTransaction(canonicalTx);

        // Recomputes the trie → emits node writes → onBeforeCommit → canonicalTx
        rollTo(state, blockHash);

        canonicalTx.commit(); // archive history + coverage (the cursor), atomically
        canonicalTx = null;
        writer.setArchiveWriteTransaction(null);
        cursor.set(b);
        if (b % 1000 == 0) {
          LOG.debug(
              "Archive trie-node replayer: block {} / {} ({} remaining)",
              b,
              toInclusive,
              toInclusive - b);
        }
      }
    } finally {
      rollbackQuietly(canonicalTx);
      closeQuietly(state);
    }
  }

  /**
   * Takes a layered state over a snapshot of the persisted head from the world state cache manager,
   * installs {@code strategy} on it and rolls it back to {@code fromExclusive}, or to the oldest
   * block the trie logs reach if the cursor is older than that and nothing has been archived yet.
   */
  private PathBasedWorldState openPositionedState(
      final long fromExclusive, final ArchiveTrieNodeStrategy strategy) {
    final PathBasedWorldState state =
        worldStateProvider
            .getWorldStateCacheManager()
            .getHeadWorldState(
                hash ->
                    blockchain
                        .getBlockHeader(hash)
                        .map(org.hyperledger.besu.plugin.data.BlockHeader.class::cast))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Replayer found no head world state to start from; will retry"));
    try {
      if (state.isTrieDisabled()) {
        throw new IllegalStateException(
            "Replayer world state has the trie disabled, so no trie nodes can be archived");
      }
      final long head =
          blockchain
              .getBlockHeader(state.blockHash())
              .map(BlockHeader::getNumber)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Replayer world state head " + state.blockHash() + " is unknown"));

      // Guard: if the cursor is older than the trie-log retention window, we cannot roll back
      // far enough to reposition.
      final ArchiveCoverageTracker coverage =
          new ArchiveCoverageTracker(worldStateStorage.getComposedWorldStateStorage());
      final long maxLayers = trieLogManager.getMaxLayersToLoad();
      final long oldestReachable = Math.max(0L, head - maxLayers);
      final long positionTarget;
      if (fromExclusive < oldestReachable) {
        if (coverage.hasCoverage()) {
          // The archive already covers blocks up to the cursor. Skipping ahead would leave blocks
          // without history entries inside the covered window, and proofs for them would silently
          // read stale nodes. Stop instead and leave the coverage at its last contiguous block.
          throw new ArchiveGapException(
              String.format(
                  "Archive trie-node replayer cannot continue: cursor %d is beyond trie-log retention"
                      + " (head=%d, maxLayers=%d), so blocks %d-%d cannot be archived. Archive proof"
                      + " coverage stays at block %d and the replayer is stopping; the archive must be"
                      + " resynced to extend it.",
                  fromExclusive,
                  head,
                  maxLayers,
                  fromExclusive + 1,
                  oldestReachable,
                  fromExclusive));
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
      } else {
        positionTarget = fromExclusive;
      }

      if (head < positionTarget) {
        throw new IllegalStateException(
            String.format(
                "Replayer snapshot head %d is behind the replayer position %d; will retry",
                head, positionTarget));
      }

      state.getWorldStateStorage().setTrieNodeStrategy(strategy);
      // No archive redirect is set yet, so any node writes while rolling back go to the discarded
      // layer transaction, not to canonical.
      rollTo(state, canonicalHash(positionTarget));
      // Those discarded writes moved the writer's continuity marker. Restore it when the archive
      // reaches the position, so the first archived block diffs against its history rather than
      // writing full entries.
      if (coverage.hasArchiveBlock(positionTarget)) {
        strategy.getTrieNodeWriter().markArchivedThrough(positionTarget);
      }
      return state;
    } catch (final RuntimeException e) {
      closeQuietly(state);
      throw e;
    }
  }

  /** The archive cannot be extended without leaving a gap, and retrying cannot fix that. */
  static final class ArchiveGapException extends IllegalStateException {
    ArchiveGapException(final String message) {
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

  private static void closeQuietly(final PathBasedWorldState state) {
    // Closing an already-closed layer logs an "access closed worldstate" warning.
    if (state != null && !state.getWorldStateStorage().isClosed()) {
      state.close();
    }
  }

  /**
   * Rolls {@code state} to {@code blockHash} using Bonsai's own trie-log rollback and roll-forward.
   * A no-op when the state is already there.
   */
  private void rollTo(final PathBasedWorldState state, final Hash blockHash) {
    if (worldStateProvider.rollFullWorldStateToBlockHash(state, blockHash, Level.WARN).isEmpty()) {
      throw new IllegalStateException(
          "Archive trie-node replayer failed to roll its world state to block " + blockHash);
    }
  }

  private Hash canonicalHash(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).orElseThrow().getHash();
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
  }
}
