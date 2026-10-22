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
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.TrailingBlockWorker;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveCoverageTracker.Coverage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.provider.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.PathBasedWorldState;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.util.log.LogUtil;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Archives trie-node history for finalized blocks once the node is in sync.
 *
 * <p>The archiver trails the finalized block, or {@code head - maxLayersToLoad / 2} on chains
 * without finality. Each run rolls a layered world state back from the persisted head to the last
 * archived block, then forward one block at a time; the capture strategy writes each block's trie
 * nodes and coverage atomically to the archive, and the layer's own writes are discarded.
 *
 * <p>Live capture calls {@link #catchUpTo} before it starts or resumes, so coverage stays
 * contiguous. If a block can no longer be archived, the archiver halts rather than leave a gap.
 */
public class FinalizedTrieNodeArchiver extends TrailingBlockWorker {

  private static final Logger LOG = LoggerFactory.getLogger(FinalizedTrieNodeArchiver.class);
  private static final int LOG_INTERVAL_SECONDS = 60;
  private static final long CATCHUP_LOG_THRESHOLD = 1_000L;
  private static final long CLOSE_TIMEOUT_SECONDS = 30L;
  private static final long CATCH_UP_BATCH_BLOCKS = 32L;

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
   * Creates a new FinalizedTrieNodeArchiver.
   *
   * @param worldStateStorage the Bonsai world state storage
   * @param worldStateProvider the world state provider for opening and rolling archiver states
   * @param trieLogManager the trie log manager for reading trie logs
   * @param blockchain the blockchain for reading block headers
   * @param executorService the executor service for running catch-ups on a separate thread
   * @param trieCapturePool the pool for computing archive history entries
   * @param shallowCheckpointInterval the shallow checkpoint interval for archive entries
   * @param deepCheckpointInterval the deep checkpoint interval for archive entries
   */
  public FinalizedTrieNodeArchiver(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final BonsaiWorldStateProvider worldStateProvider,
      final TrieLogManager trieLogManager,
      final Blockchain blockchain,
      final ScheduledExecutorService executorService,
      final ExecutorService trieCapturePool,
      final int shallowCheckpointInterval,
      final int deepCheckpointInterval) {
    super(blockchain, executorService, CLOSE_TIMEOUT_SECONDS);
    this.worldStateStorage = worldStateStorage;
    this.worldStateProvider = worldStateProvider;
    this.trieLogManager = trieLogManager;
    this.blockchain = blockchain;
    this.trieCapturePool = trieCapturePool;
    this.shallowCheckpointInterval = shallowCheckpointInterval;
    this.deepCheckpointInterval = deepCheckpointInterval;
    this.coverage = new ArchiveCoverageTracker(worldStateStorage.getComposedWorldStateStorage());
  }

  /** Starts archiving from the end of the archive's coverage and follows new canonical heads. */
  public void start() {
    if (closed) {
      return;
    }
    archivedBlockNumber.set(coverage.getCoverage().map(Coverage::lastBlock).orElse(0L));
    targetBlock.set(archiveTarget(blockchain.getChainHeadBlockNumber()));
    LOG.info(
        "Finalized trie-node archiver starting: archived={} target={}",
        archivedBlockNumber.get(),
        targetBlock.get());

    startObserving();
    scheduleCatchUpIfNeeded();
  }

  // Trailing by half the retention window leaves the other half as slack for the head to advance
  // before a run rolls back to the archived block.
  @VisibleForTesting
  long archiveTarget(final long headNumber) {
    return blockchain
        .getFinalized()
        .flatMap(blockchain::getBlockHeader)
        .map(BlockHeader::getNumber)
        .orElse(Math.max(0L, headNumber - trieLogManager.getMaxLayersToLoad() / 2));
  }

  @Override
  protected long targetFor(final long headNumber) {
    return archiveTarget(headNumber);
  }

  @Override
  protected long position() {
    return archivedBlockNumber.get();
  }

  @Override
  protected boolean shouldCatchUp() {
    // Every run rolls back from the head, so wait for a batch of blocks rather than run per block.
    final long batch =
        Math.clamp(trieLogManager.getMaxLayersToLoad() / 4, 1L, CATCH_UP_BATCH_BLOCKS);
    return targetBlock.get() - archivedBlockNumber.get() >= batch;
  }

  /**
   * Archives every block from the end of coverage up to the target and waits for it to finish.
   * Called by live capture before it starts or resumes.
   *
   * @param target the last block to archive; must be final
   * @return true if the archive now reaches the target or has no coverage to extend
   */
  public boolean catchUpTo(final long target) {
    return runAndWait(
        () -> {
          if (advanceToArchiveProgress().isEmpty()) {
            return true;
          }
          archiveBlocks(archivedBlockNumber.get(), target);
          return archivedBlockNumber.get() >= target;
        });
  }

  @Override
  protected void catchUp() {
    advanceToArchiveProgress();
    final long from = archivedBlockNumber.get();
    final long target = targetBlock.get();
    try {
      archiveBlocks(from, target);
    } catch (final ArchiveGapException e) {
      halt();
      LOG.error(e.getMessage());
    } finally {
      final long reached = archivedBlockNumber.get();
      if (reached > from && reached >= target) {
        LOG.info("Finalized trie-node archiver: fully caught up at block {}", reached);
      } else if (reached > from) {
        LOG.debug("Finalized trie-node archiver: advanced to {} (target {})", reached, target);
      }
    }
  }

  @VisibleForTesting
  void archiveBlocks(final long fromExclusive, final long toInclusive) {
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
          blockNumber <= toInclusive && !closed;
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
    return ArchiveTrieNodeStrategy.createFinalizedArchiverStrategy(
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
    final long maxLayers = trieLogManager.getMaxLayersToLoad();
    final long oldestReachable = Math.max(0L, head - maxLayers);
    if (fromExclusive >= oldestReachable) {
      return fromExclusive;
    }
    final String outOfRetention =
        String.format(
            "blocks %d-%d are outside trie-log retention (head=%d, maxLayers=%d)",
            fromExclusive + 1, oldestReachable, head, maxLayers);
    // Skipping ahead would leave a gap where proofs silently read stale nodes.
    if (coverage.hasCoverage()) {
      throw new ArchiveGapException(outOfRetention);
    }
    // Nothing archived yet, so start from the oldest reachable block.
    LOG.warn("Finalized trie-node archiver skipping {}", outOfRetention);
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
                "Finalized trie-node archiver progress: block {}/{} ({} remaining)",
                blockNumber,
                endBlock,
                endBlock - blockNumber),
        shouldLogProgress,
        LOG_INTERVAL_SECONDS);
  }

  static final class ArchiveGapException extends IllegalStateException {
    ArchiveGapException(final String reason) {
      super(
          "Finalized trie-node archiver stopping: "
              + reason
              + "; archive coverage stays where it is until the archive is resynced");
    }
  }

  private void rollbackQuietly(final SegmentedKeyValueStorageTransaction tx) {
    try {
      tx.rollback();
    } catch (final Exception e) {
      LOG.warn("Failed to roll back finalized archiver transaction", e);
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
          "Finalized trie-node archiver failed to roll its world state to block " + blockHash);
    }
  }

  private Hash canonicalHash(final long blockNumber) {
    return blockchain.getBlockHeader(blockNumber).orElseThrow().getHash();
  }
}
