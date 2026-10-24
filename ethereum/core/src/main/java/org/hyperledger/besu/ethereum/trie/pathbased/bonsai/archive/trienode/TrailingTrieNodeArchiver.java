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
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveCoverageTracker.Coverage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.PathBasedWorldState;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.util.log.LogUtil;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Archives trie-node history for blocks that can no longer be reorged out, while the capture mode
 * is {@link ArchiveTrieNodeStrategy.CaptureMode#TRAILING}.
 *
 * <p>The trailing sync coordinator decides how far to archive and runs the archiver on its thread.
 * Each run rolls a layered world state back from the persisted head to the last archived block,
 * then forward one block at a time; the capture strategy writes each block's trie nodes and
 * coverage atomically to the archive, and the layer's own writes are discarded.
 *
 * <p>Inline capture calls {@link #catchUpTo} before it starts or resumes, so coverage stays
 * contiguous. If a block's trie log is missing, the archiver halts rather than leave a gap.
 */
public class TrailingTrieNodeArchiver {

  private static final Logger LOG = LoggerFactory.getLogger(TrailingTrieNodeArchiver.class);
  private static final int LOG_INTERVAL_SECONDS = 60;
  private static final long CATCHUP_LOG_THRESHOLD = 1_000L;

  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final BonsaiWorldStateProvider worldStateProvider;
  private final TrieLogManager trieLogManager;
  private final Blockchain blockchain;
  private final ExecutorService trieCapturePool;
  private final int shallowCheckpointInterval;
  private final int deepCheckpointInterval;
  private final ArchiveCoverageTracker coverage;
  private final AtomicBoolean shouldLogProgress = new AtomicBoolean(true);

  final AtomicLong archivedBlockNumber = new AtomicLong(0L);

  /**
   * Creates a new TrailingTrieNodeArchiver.
   *
   * @param worldStateStorage the Bonsai world state storage
   * @param worldStateProvider the world state provider for opening and rolling archiver states
   * @param trieLogManager the trie log manager for reading trie logs
   * @param blockchain the blockchain for reading block headers
   * @param trieCapturePool the pool for computing archive history entries
   * @param shallowCheckpointInterval the shallow checkpoint interval for archive entries
   * @param deepCheckpointInterval the deep checkpoint interval for archive entries
   */
  public TrailingTrieNodeArchiver(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final BonsaiWorldStateProvider worldStateProvider,
      final TrieLogManager trieLogManager,
      final Blockchain blockchain,
      final ExecutorService trieCapturePool,
      final int shallowCheckpointInterval,
      final int deepCheckpointInterval) {
    this.worldStateStorage = worldStateStorage;
    this.worldStateProvider = worldStateProvider;
    this.trieLogManager = trieLogManager;
    this.blockchain = blockchain;
    this.trieCapturePool = trieCapturePool;
    this.shallowCheckpointInterval = shallowCheckpointInterval;
    this.deepCheckpointInterval = deepCheckpointInterval;
    this.coverage = new ArchiveCoverageTracker(worldStateStorage.getComposedWorldStateStorage());
  }

  /**
   * Archives every block from the end of coverage up to the target on the calling thread. Does
   * nothing if the archive has no coverage to extend.
   *
   * @param target the last block to archive; must be final
   * @param keepGoing checked before each block; archiving stops between blocks once it is false
   * @return true if the archive now reaches the target or has no coverage to extend
   * @throws ArchiveGapException if a block's trie log is missing
   */
  public boolean catchUpTo(final long target, final BooleanSupplier keepGoing) {
    if (advanceToArchiveProgress().isEmpty()) {
      return true;
    }
    archiveBlocks(archivedBlockNumber.get(), target, keepGoing);
    return archivedBlockNumber.get() >= target;
  }

  /**
   * Archives blocks from the end of the archive's coverage up to the target on the calling thread.
   *
   * @param target the last block to archive; must be final
   * @param keepGoing checked before each block; archiving stops between blocks once it is false
   * @throws ArchiveGapException if a block's trie log is missing
   */
  public void archiveTo(final long target, final BooleanSupplier keepGoing) {
    advanceToArchiveProgress();
    archiveBlocks(archivedBlockNumber.get(), target, keepGoing);
  }

  /**
   * Returns the last block the archiver has archived.
   *
   * @return the archived block number
   */
  public long getArchivedBlockNumber() {
    return archivedBlockNumber.get();
  }

  /**
   * Returns the last block covered by the archive, which inline capture can extend while the
   * archiver is idle.
   *
   * @return the end of coverage, or empty if the archive has no coverage
   */
  public OptionalLong coverageEnd() {
    return coverage
        .getCoverage()
        .map(c -> OptionalLong.of(c.lastBlock()))
        .orElseGet(OptionalLong::empty);
  }

  @VisibleForTesting
  void archiveBlocks(
      final long fromExclusive, final long toInclusive, final BooleanSupplier keepGoing) {
    if (toInclusive <= fromExclusive) {
      return;
    }
    final boolean shouldLog = toInclusive - fromExclusive >= CATCHUP_LOG_THRESHOLD;
    // Not closed after the run, as that would shut down the shared capture pool.
    final ArchiveTrieNodeStrategy strategy = createArchiveStrategy();
    final PathBasedWorldState state = openArchiveState(fromExclusive, strategy);
    try {
      // Opening the state can move the archived block number forward past fromExclusive.
      for (long blockNumber = archivedBlockNumber.get() + 1;
          blockNumber <= toInclusive && keepGoing.getAsBoolean();
          blockNumber++) {
        archiveBlock(state, strategy.getTrieNodeWriter(), blockNumber);
        if (shouldLog) {
          logProgress(blockNumber, toInclusive);
        }
      }
    } finally {
      closeQuietly(state);
    }
  }

  private void archiveBlock(
      final PathBasedWorldState state, final ArchiveTrieNodeWriter writer, final long blockNumber) {
    final Hash blockHash = canonicalHash(blockNumber);
    requireTrieLog(blockNumber, blockHash);
    final SegmentedKeyValueStorageTransaction tx =
        worldStateStorage.getComposedWorldStateStorage().startLowPriorityTransaction();
    writer.setArchiveWriteTransaction(tx);
    try {
      rollTo(state, blockHash);
      tx.commit();
    } catch (final RuntimeException e) {
      rollbackQuietly(tx);
      throw e;
    } finally {
      writer.setArchiveWriteTransaction(null);
    }
    archivedBlockNumber.set(blockNumber);
  }

  private void requireTrieLog(final long blockNumber, final Hash blockHash) {
    // A missing trie log cannot be fixed by retrying, so treat it as a gap.
    if (trieLogManager.getTrieLogLayer(blockHash).isEmpty()) {
      throw new ArchiveGapException("the trie log for block " + blockNumber + " is missing");
    }
  }

  private ArchiveTrieNodeStrategy createArchiveStrategy() {
    return ArchiveTrieNodeStrategy.createTrailingArchiverStrategy(
        worldStateStorage.getComposedWorldStateStorage(),
        trieCapturePool,
        shallowCheckpointInterval,
        deepCheckpointInterval);
  }

  private PathBasedWorldState openArchiveState(
      final long fromExclusive, final ArchiveTrieNodeStrategy strategy) {
    final PathBasedWorldState state = openHeadWorldState();
    try {
      requireTrieEnabled(state);
      final long startBlock = resolveStartBlock(fromExclusive, headBlockNumber(state));
      positionState(state, strategy, startBlock);
      return state;
    } catch (final RuntimeException e) {
      closeQuietly(state);
      throw e;
    }
  }

  private PathBasedWorldState openHeadWorldState() {
    return worldStateProvider
        .getWorldStateCacheManager()
        .getHeadWorldState(
            hash ->
                blockchain
                    .getBlockHeader(hash)
                    .map(org.hyperledger.besu.plugin.data.BlockHeader.class::cast))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Archiver found no head world state to start from; will retry"));
  }

  private static void requireTrieEnabled(final PathBasedWorldState state) {
    if (state.isTrieDisabled()) {
      throw new IllegalStateException(
          "Archiver world state has the trie disabled, so no trie nodes can be archived");
    }
  }

  private long headBlockNumber(final PathBasedWorldState state) {
    return blockchain
        .getBlockHeader(state.blockHash())
        .map(BlockHeader::getNumber)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Archiver world state head " + state.blockHash() + " is unknown"));
  }

  private long resolveStartBlock(final long fromExclusive, final long head) {
    if (head < fromExclusive) {
      throw new IllegalStateException(
          "Archiver snapshot head " + head + " is behind the archiver position " + fromExclusive);
    }
    // Archive nodes never prune trie logs, so any archived block can be rolled back to; a missing
    // trie log is caught per block instead.
    if (coverage.hasCoverage()) {
      return fromExclusive;
    }
    // Nothing archived yet, so start no further back than an ordinary Bonsai node could roll.
    final long maxLayers = trieLogManager.getMaxLayersToLoad();
    final long oldestReachable = Math.max(0L, head - maxLayers);
    if (fromExclusive >= oldestReachable) {
      return fromExclusive;
    }
    LOG.warn(
        "Trailing trie-node archiver has no coverage; starting at block {} (head={}, maxLayers={})",
        oldestReachable + 1,
        head,
        maxLayers);
    archivedBlockNumber.set(oldestReachable);
    return oldestReachable;
  }

  private void positionState(
      final PathBasedWorldState state,
      final ArchiveTrieNodeStrategy strategy,
      final long startBlock) {
    state.getWorldStateStorage().setTrieNodeStrategy(strategy);
    // No archive transaction is set yet, so writes while rolling back stay in the discarded layer.
    rollTo(state, canonicalHash(startBlock));
    // Rolling back moved the writer's continuity marker; restore it so the next block is diffed.
    if (coverage.hasArchiveBlock(startBlock)) {
      strategy.getTrieNodeWriter().markArchivedThrough(startBlock);
    }
  }

  private Optional<Coverage> advanceToArchiveProgress() {
    // Live capture can extend coverage while the archiver is idle.
    final Optional<Coverage> progress = coverage.getCoverage();
    progress.ifPresent(c -> archivedBlockNumber.accumulateAndGet(c.lastBlock(), Math::max));
    return progress;
  }

  private void logProgress(final long blockNumber, final long endBlock) {
    LogUtil.throttledLog(
        () ->
            LOG.info(
                "Trailing trie-node archiver progress: block {}/{} ({} remaining)",
                blockNumber,
                endBlock,
                endBlock - blockNumber),
        shouldLogProgress,
        LOG_INTERVAL_SECONDS);
  }

  /** Thrown when a block's trie log is missing, so archiving cannot continue without a gap. */
  public static final class ArchiveGapException extends IllegalStateException {
    /**
     * Creates the exception.
     *
     * @param reason why the archiver cannot continue
     */
    public ArchiveGapException(final String reason) {
      super(
          "Trailing trie-node archiver stopping: "
              + reason
              + "; archive coverage stays where it is until the archive is resynced");
    }
  }

  private void rollbackQuietly(final SegmentedKeyValueStorageTransaction tx) {
    try {
      tx.rollback();
    } catch (final Exception e) {
      LOG.warn("Failed to roll back trailing archiver transaction", e);
    }
  }

  private static void closeQuietly(final PathBasedWorldState state) {
    // Closing an already-closed layer logs a warning.
    if (!state.getWorldStateStorage().isClosed()) {
      state.close();
    }
  }

  private void rollTo(final PathBasedWorldState state, final Hash blockHash) {
    if (worldStateProvider.rollFullWorldStateToBlockHash(state, blockHash, Level.WARN).isEmpty()) {
      throw new IllegalStateException(
          "Trailing trie-node archiver failed to roll its world state to block " + blockHash);
    }
  }

  private Hash canonicalHash(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).orElseThrow().getHash();
  }
}
