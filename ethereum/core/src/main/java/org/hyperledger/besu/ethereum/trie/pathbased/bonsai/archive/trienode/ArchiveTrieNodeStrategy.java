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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;

import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.trienode.BonsaiTrieNodeStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.trienode.TrieNodeKey;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.trienode.TrieNodeStrategy;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TrieNodeStrategy} that archives every trie-node write into {@code
 * TRIE_BRANCH_STORAGE_ARCHIVE} so historical {@code eth_getProof} requests don't need trie-log
 * replay.
 *
 * <p>Each put delegates to {@code base} (live trie) first, then — if the block is captured inline —
 * reads the prior node value from storage (where it is typically hot in the top few in-memory
 * layers) and calls {@link ArchiveTrieNodeWriter#capture} to queue an async history-entry write.
 * Workers compute the history entry ({@link ArchiveNodeHistoryStore#getLatestBefore} + encode
 * FULL/DIFF) off the import thread. Results are joined and applied to the transaction in {@link
 * #onBeforeCommit}, which runs immediately before commit.
 *
 * <p>Reading the prior node on the calling thread — rather than inside a worker — avoids disk I/O
 * in the common case (the prior value is hot in the in-memory layered chain) and eliminates any
 * need for worker threads to access the layered storage. An archiving gap — a switch from trailing
 * mode, or a restart — forces the next block to write FULL, since the newest archive entry no
 * longer matches the live trie.
 */
public class ArchiveTrieNodeStrategy
    implements TrieNodeStrategy, Synchronizer.InSyncListener, Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(ArchiveTrieNodeStrategy.class);

  /**
   * Which component writes trie-node history into the archive. Only one is active at a time; they
   * are alternative ways of storing the same data.
   */
  public enum CaptureMode {
    /** Each block's trie nodes are archived as the block is imported. */
    INLINE,
    /** The {@link TrailingTrieNodeArchiver} archives blocks once they can no longer be reorged. */
    TRAILING
  }

  private final TrieNodeStrategy base;
  private final ArchiveTrieNodeWriter trieNodeWriter;

  // Who writes the archive. Starts INLINE so the trailing archiver stays idle until a block shows
  // it is needed; the first inline block still catches up first.
  private volatile CaptureMode captureMode = CaptureMode.INLINE;

  // Set once the blocks before inline capture started are archived, so coverage stays contiguous.
  private volatile boolean inlineCaughtUp = false;

  // Set when that catch-up fails; inline capture is not retried until the next in-sync event.
  private volatile boolean inlineCatchUpFailed = false;

  // A node behind the network imports only final blocks. Assumed in sync until an event says
  // otherwise, so a restarted node at the head cannot archive non-final blocks before the first
  // event; a fresh node still captures inline since its finalized block is not imported yet.
  private volatile boolean inSync = true;

  // True when a finalized block is imported, so blocks above it can still be reorged out.
  private volatile BooleanSupplier hasFinalizedBlock = () -> false;

  // Told of every mode change, on the importing thread.
  private volatile Consumer<CaptureMode> captureModeListener = mode -> {};

  // Archives the blocks up to the given one, returning whether it succeeded. Runs before inline
  // capture starts so coverage never skips over blocks the trailing archiver had not reached.
  private volatile LongPredicate catchUpTo = target -> true;

  // Block number is constant within a transaction; cache it to avoid a storage read per node write.
  private final Cache<SegmentedKeyValueStorageTransaction, Long> txBlockNumberCache =
      Caffeine.newBuilder().weakKeys().build();

  // Whether to archive is decided once per transaction, so a block is archived whole or not at all.
  private final Cache<SegmentedKeyValueStorageTransaction, Boolean> txCaptureCache =
      Caffeine.newBuilder().weakKeys().build();

  /**
   * Builds an archiving strategy over {@code liveStorage}, owning a worker pool for async
   * history-entry computation.
   *
   * @param liveStorage the live world-state storage to archive writes from
   * @param trieCapturePool the worker pool, owned and shut down by the returned strategy
   * @param shallowCheckpointInterval FULL-checkpoint interval for shallow nodes
   * @param deepCheckpointInterval FULL-checkpoint interval for deep nodes
   * @return an archiving {@link TrieNodeStrategy} ready to install via {@code setTrieNodeStrategy}
   */
  public static ArchiveTrieNodeStrategy createArchiveStrategy(
      final SegmentedKeyValueStorage liveStorage,
      final ExecutorService trieCapturePool,
      final int shallowCheckpointInterval,
      final int deepCheckpointInterval) {
    return new ArchiveTrieNodeStrategy(
        new BonsaiTrieNodeStrategy(),
        new ArchiveTrieNodeWriter(
            new ArchiveNodeHistoryStore(liveStorage),
            new ArchiveCoverageTracker(liveStorage),
            trieCapturePool,
            shallowCheckpointInterval,
            deepCheckpointInterval));
  }

  /**
   * Like {@link #createArchiveStrategy} but for the trailing trie-node archiver: every block is
   * captured inline (the archiver only ever processes final blocks) and the caller drives
   * archive-write routing via {@link ArchiveTrieNodeWriter#setArchiveWriteTransaction}. Reads (diff
   * base / coverage) still use the supplied canonical storage.
   */
  public static ArchiveTrieNodeStrategy createTrailingArchiverStrategy(
      final SegmentedKeyValueStorage canonicalLiveStorage,
      final ExecutorService trieCapturePool,
      final int shallowCheckpointInterval,
      final int deepCheckpointInterval) {
    // With no in-sync events and no finality signal, the mode stays INLINE.
    return new ArchiveTrieNodeStrategy(
        new BonsaiTrieNodeStrategy(),
        new ArchiveTrieNodeWriter(
            new ArchiveNodeHistoryStore(canonicalLiveStorage),
            new ArchiveCoverageTracker(canonicalLiveStorage),
            trieCapturePool,
            shallowCheckpointInterval,
            deepCheckpointInterval));
  }

  /** Returns the underlying writer so the archiver can call {@code setArchiveWriteTransaction}. */
  public ArchiveTrieNodeWriter getTrieNodeWriter() {
    return trieNodeWriter;
  }

  /**
   * Supplies whether a finalized block is imported. While the node is in sync and one is, blocks at
   * the head can still be reorged out, so the trailing archiver archives them once they are final
   * instead of capturing them inline.
   */
  public void setHasFinalizedBlock(final BooleanSupplier hasFinalizedBlock) {
    this.hasFinalizedBlock =
        Objects.requireNonNull(hasFinalizedBlock, "hasFinalizedBlock must not be null");
  }

  /**
   * Supplies the catch-up run before inline capture starts or resumes. It is called on the
   * importing thread with the block before the first one to be captured, and must archive every
   * block up to it before returning true.
   */
  public void setCatchUpTo(final LongPredicate catchUpTo) {
    this.catchUpTo = Objects.requireNonNull(catchUpTo, "catchUpTo must not be null");
  }

  /**
   * Supplies the listener told of every capture mode change. It is called on the importing thread,
   * after any catch-up and before the block is captured.
   */
  public void setCaptureModeListener(final Consumer<CaptureMode> captureModeListener) {
    this.captureModeListener =
        Objects.requireNonNull(captureModeListener, "captureModeListener must not be null");
  }

  /**
   * Returns which component currently writes the archive.
   *
   * @return the capture mode
   */
  public CaptureMode getCaptureMode() {
    return captureMode;
  }

  /**
   * @param base the delegate strategy for the live trie
   * @param trieNodeWriter the writer that persists archived history entries
   */
  @VisibleForTesting
  public ArchiveTrieNodeStrategy(
      final TrieNodeStrategy base, final ArchiveTrieNodeWriter trieNodeWriter) {
    this.base = Objects.requireNonNull(base);
    this.trieNodeWriter = Objects.requireNonNull(trieNodeWriter);
  }

  @Override
  public void onInSyncStatusChange(final boolean inSync) {
    this.inSync = inSync;
    inlineCatchUpFailed = false;
  }

  private static long readBlockNumber(final SegmentedKeyValueStorage storage) {
    return storage
        .get(TRIE_BRANCH_STORAGE, WORLD_BLOCK_NUMBER_KEY)
        .map(b -> Bytes.wrap(b).toLong() + 1L)
        .orElse(0L);
  }

  private boolean shouldCaptureBlock(
      final SegmentedKeyValueStorageTransaction transaction, final long block) {
    // Not computed inside the cache: a catch-up can block for a long time.
    Boolean capture = txCaptureCache.getIfPresent(transaction);
    if (capture == null) {
      capture = decideCapture(block);
      txCaptureCache.put(transaction, capture);
    }
    return capture;
  }

  private boolean decideCapture(final long block) {
    if (block == 0L) {
      return true;
    }
    // In sync with finality, the head can still be reorged out. Otherwise every imported block is
    // final: the node is behind the network, the finalized block is not imported yet, or the chain
    // has no finality signal (BFT blocks are final on import).
    if (inSync && hasFinalizedBlock.getAsBoolean()) {
      switchTo(CaptureMode.TRAILING, block);
      return false;
    }
    if (captureMode == CaptureMode.INLINE && inlineCaughtUp) {
      return true;
    }
    if (inlineCatchUpFailed) {
      return false;
    }
    if (catchUpTo.test(block - 1L)) {
      switchTo(CaptureMode.INLINE, block);
      inlineCaughtUp = true;
      return true;
    }
    LOG.warn(
        "Archive trie-node capture could not archive the blocks before {}; the trailing archiver"
            + " stays active until the next in-sync change",
        block);
    inlineCatchUpFailed = true;
    switchTo(CaptureMode.TRAILING, block);
    return false;
  }

  private void switchTo(final CaptureMode mode, final long block) {
    if (mode == CaptureMode.TRAILING) {
      inlineCaughtUp = false;
    }
    if (captureMode != mode) {
      captureMode = mode;
      LOG.info("Archive trie-node capture mode is now {} from block {}", mode, block);
      captureModeListener.accept(mode);
    }
  }

  // Bonsai re-commits unchanged nodes along a touched path; skip archiving them to avoid
  // inflating the diff-chain counter. Creations (prior == null) are never no-ops.
  private boolean isNoOpRewrite(final Bytes prior, final Bytes node) {
    return prior != null && prior.equals(node);
  }

  @Override
  public Optional<Bytes> getTrieNode(
      final SegmentedKeyValueStorage storage, final Bytes key, final Bytes32 nodeHash) {
    return base.getTrieNode(storage, key, nodeHash);
  }

  @Override
  public void putTrieNode(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Bytes key,
      final Bytes32 nodeHash,
      final Bytes node) {
    final long block = txBlockNumberCache.get(transaction, ignored -> readBlockNumber(storage));
    base.putTrieNode(storage, transaction, key, nodeHash, node);
    if (shouldCaptureBlock(transaction, block)) {
      final Bytes prior = readPriorNode(storage, key, block);
      if (isNoOpRewrite(prior, node)) {
        return;
      }
      capture(key, block, node, prior, transaction);
    }
  }

  @Override
  public void removeTrieNode(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Bytes key) {
    final long block = txBlockNumberCache.get(transaction, ignored -> readBlockNumber(storage));
    base.removeTrieNode(storage, transaction, key);
    if (shouldCaptureBlock(transaction, block)) {
      final Bytes prior = readPriorNode(storage, key, block);
      // Removing a node that doesn't exist is a no-op for the archive.
      if (prior != null) {
        capture(key, block, null, prior, transaction);
      }
    }
  }

  // Genesis (block 0) has no prior node by definition.
  private Bytes readPriorNode(
      final SegmentedKeyValueStorage storage, final Bytes key, final long block) {
    return block == 0L ? null : base.getTrieNode(storage, key, Bytes32.ZERO).orElse(null);
  }

  private void capture(
      final Bytes key,
      final long block,
      final Bytes node,
      final Bytes prior,
      final SegmentedKeyValueStorageTransaction transaction) {
    trieNodeWriter.capture(
        ArchiveNodeKey.of(key), TrieNodeKey.location(key), block, node, prior, transaction);
  }

  @Override
  public void onBeforeCommit(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction) {
    txBlockNumberCache.invalidate(transaction);
    txCaptureCache.invalidate(transaction);
    trieNodeWriter.onBeforeCommit(transaction);
  }

  @Override
  public void onRollback(final SegmentedKeyValueStorageTransaction transaction) {
    txBlockNumberCache.invalidate(transaction);
    txCaptureCache.invalidate(transaction);
    trieNodeWriter.onRollback(transaction);
  }

  @Override
  public void close() {
    trieNodeWriter.close();
  }
}
