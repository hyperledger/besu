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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

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
 * <p>Each put delegates to {@code base} (live trie) first, then — if the archive gate is open —
 * reads the prior node value from storage (where it is typically hot in the top few in-memory
 * layers) and calls {@link ArchiveTrieNodeWriter#capture} to queue an async history-entry write.
 * Workers compute the history entry ({@link ArchiveNodeHistoryStore#getLatestBefore} + encode
 * FULL/DIFF) off the import thread. Results are joined and applied to the transaction in {@link
 * #onBeforeCommit}, which runs immediately before commit.
 *
 * <p>Reading the prior node on the calling thread — rather than inside a worker — avoids disk I/O
 * in the common case (the prior value is hot in the in-memory layered chain) and eliminates any
 * need for worker threads to access the layered storage. An archiving gap — gate closed then
 * reopened, or a restart — forces the next block to write FULL, since the newest archive entry no
 * longer matches the live trie.
 */
public class ArchiveTrieNodeStrategy
    implements TrieNodeStrategy, Synchronizer.InSyncListener, Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(ArchiveTrieNodeStrategy.class);

  private final TrieNodeStrategy base;
  private final ArchiveTrieNodeWriter trieNodeWriter;

  // Starts true (archive everything) until the first in-sync event. Safe default for initial sync
  // and no-peers startup
  private final AtomicBoolean archiving = new AtomicBoolean(true);

  // True when an external chain estimate exists (e.g. a peer or CL that knows the chain head)
  private volatile BooleanSupplier hasChainEstimate;

  // True when the last in-sync event said the node is behind the network, so every block it imports
  // is already final. Otherwise capture may be running at the head.
  private volatile boolean behindNetwork = false;

  // Highest block that can no longer be reorged out. Consulted unless the node is behind the
  // network.
  private volatile LongSupplier finalizedBlockLimit = () -> Long.MAX_VALUE;

  // Archives the blocks up to the given one that were imported while capture was off, returning
  // whether it succeeded. Runs before capture resumes so coverage never skips over them.
  private volatile LongPredicate catchUpTo = target -> true;

  // Set when capture starts or resumes, cleared once the catch-up before its first block succeeds.
  private volatile boolean needsCatchUp = true;

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
            deepCheckpointInterval),
        () -> false);
  }

  /**
   * Like {@link #createArchiveStrategy} but for the finalized trie-node archiver: capture is always
   * on (the archiver only ever processes final blocks) and the caller drives archive-write routing
   * via {@link ArchiveTrieNodeWriter#setArchiveWriteTransaction}. Reads (diff base / coverage)
   * still use the supplied canonical storage.
   */
  public static ArchiveTrieNodeStrategy createFinalizedArchiverStrategy(
      final SegmentedKeyValueStorage canonicalLiveStorage,
      final ExecutorService trieCapturePool,
      final int shallowCheckpointInterval,
      final int deepCheckpointInterval) {
    final ArchiveTrieNodeStrategy strategy =
        new ArchiveTrieNodeStrategy(
            new BonsaiTrieNodeStrategy(),
            new ArchiveTrieNodeWriter(
                new ArchiveNodeHistoryStore(canonicalLiveStorage),
                new ArchiveCoverageTracker(canonicalLiveStorage),
                trieCapturePool,
                shallowCheckpointInterval,
                deepCheckpointInterval),
            () -> true); // hasChainEstimate irrelevant; keep archiving always on
    strategy.onInSyncStatusChange(false); // force archiving = true
    return strategy;
  }

  /** Returns the underlying writer so the archiver can call {@code setArchiveWriteTransaction}. */
  public ArchiveTrieNodeWriter getTrieNodeWriter() {
    return trieNodeWriter;
  }

  /**
   * Supplies whether an external chain estimate exists. Must be set before the in-sync subscription
   * is registered, since it is consulted from {@link #onInSyncStatusChange(boolean)}.
   */
  public void setHasChainEstimate(final BooleanSupplier hasChainEstimate) {
    this.hasChainEstimate =
        Objects.requireNonNull(hasChainEstimate, "hasChainEstimate must not be null");
  }

  /**
   * Supplies the highest block that can no longer be reorged out. Unless the node is behind the
   * network, capture may be running at the head and only archives blocks up to this limit; the
   * finalized archiver archives the rest once they are final.
   */
  public void setFinalizedBlockLimit(final LongSupplier finalizedBlockLimit) {
    this.finalizedBlockLimit =
        Objects.requireNonNull(finalizedBlockLimit, "finalizedBlockLimit must not be null");
  }

  /**
   * Supplies the catch-up run before capture starts or resumes. It is called on the importing
   * thread with the block before the first one to be captured, and must archive every block up to
   * it before returning true.
   */
  public void setCatchUpTo(final LongPredicate catchUpTo) {
    this.catchUpTo = Objects.requireNonNull(catchUpTo, "catchUpTo must not be null");
  }

  /**
   * @param base the delegate strategy for the live trie
   * @param trieNodeWriter the writer that persists archived history entries
   * @param hasChainEstimate supplies whether an external chain estimate exists (see field)
   */
  @VisibleForTesting
  public ArchiveTrieNodeStrategy(
      final TrieNodeStrategy base,
      final ArchiveTrieNodeWriter trieNodeWriter,
      final BooleanSupplier hasChainEstimate) {
    this.base = Objects.requireNonNull(base);
    this.trieNodeWriter = Objects.requireNonNull(trieNodeWriter);
    this.hasChainEstimate =
        Objects.requireNonNull(hasChainEstimate, "hasChainEstimate must not be null");
  }

  @Override
  public void onInSyncStatusChange(final boolean inSync) {
    behindNetwork = !inSync;
    // A peer-less node reports "in sync" forever, so only trust a corroborated in-sync signal.
    setArchiving(!inSync || !hasChainEstimate.getAsBoolean());
  }

  @VisibleForTesting
  void setArchiving(final boolean archive) {
    if (!archiving.getAndSet(archive) && archive) {
      needsCatchUp = true;
    }
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
    if (!archiving.get()) {
      return false;
    }
    if (!behindNetwork && block > finalizedBlockLimit.getAsLong()) {
      return false;
    }
    return catchUpBeforeCapture(block);
  }

  private boolean catchUpBeforeCapture(final long block) {
    if (!needsCatchUp) {
      return true;
    }
    if (catchUpTo.test(block - 1L)) {
      needsCatchUp = false;
      return true;
    }
    LOG.warn(
        "Archive trie-node capture could not archive the blocks before {}; it stays off until it"
            + " is next enabled, and the finalized archiver will archive them",
        block);
    archiving.set(false);
    return false;
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
