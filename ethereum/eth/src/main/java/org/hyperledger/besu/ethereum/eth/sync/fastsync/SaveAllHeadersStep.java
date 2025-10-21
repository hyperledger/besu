/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.hyperledger.besu.util.log.LogUtil.throttledLog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates backward header chain continuity (boundary validation only), saves headers to DB, and
 * tracks completed ranges. Thread-safe for parallel, out-of-order execution.
 *
 * <p>This step validates only the boundaries between batches using in-memory hash maps. Internal
 * chain continuity within each batch is already validated by GetHeadersFromPeerTask.
 */
public class SaveAllHeadersStep implements Function<List<BlockHeader>, Stream<Void>> {
  private static final Logger LOG = LoggerFactory.getLogger(SaveAllHeadersStep.class);
  private static final int LOG_PROGRESS_INTERVAL = 1000;
  private static final int LOG_REPEAT_DELAY_SECONDS = 30;
  private static final int PERSIST_PROGRESS_INTERVAL = 5000; // Persist every 5000 headers

  private final MutableBlockchain blockchain;
  private final FastSyncState fastSyncState;
  private final FastSyncStateStorage fastSyncStateStorage;

  // Track which block ranges have been completed
  private final Set<Long> completedRangeStarts = ConcurrentHashMap.newKeySet();

  // Highest contiguous block from pivot (the resume point)
  private final AtomicLong highestContiguousBlock;

  // Lowest block seen (may have gaps above it)
  private final AtomicLong lowestSeenBlock;

  // In-memory boundary validation maps (for connecting ranges)
  // Map: block number -> parent hash (for lowest block in each range)
  private final ConcurrentHashMap<Long, Hash> lowestBlockParentHashes = new ConcurrentHashMap<>();

  // Map: block number -> block hash (for highest block in each range)
  private final ConcurrentHashMap<Long, Hash> highestBlockHashes = new ConcurrentHashMap<>();

  private final AtomicBoolean shouldLog = new AtomicBoolean(true);
  private final AtomicLong totalHeadersSaved = new AtomicLong(0);

  /**
   * Creates a new SaveAllHeadersStep with progress persistence.
   *
   * @param blockchain the blockchain to store headers in
   * @param metricsSystem the metrics system (unused but kept for consistency)
   * @param pivotBlockHash the pivot block hash (unused but kept for API compatibility)
   * @param fastSyncState the fast sync state to update with progress
   * @param fastSyncStateStorage the storage for persisting progress
   */
  public SaveAllHeadersStep(
      final MutableBlockchain blockchain,
      final MetricsSystem metricsSystem,
      final Hash pivotBlockHash,
      final FastSyncState fastSyncState,
      final FastSyncStateStorage fastSyncStateStorage) {
    this.blockchain = blockchain;
    this.fastSyncState = fastSyncState;
    this.fastSyncStateStorage = fastSyncStateStorage;

    // Initialize from persisted state or use pivot block number
    final long initialBlock =
        fastSyncState
            .getLowestContiguousBlockHeaderDownloaded()
            .orElse(fastSyncState.getPivotBlockNumber().orElse(0L));

    this.highestContiguousBlock = new AtomicLong(initialBlock);
    this.lowestSeenBlock = new AtomicLong(initialBlock);

    LOG.debug("SaveAllHeadersStep initialized with starting block: {}", initialBlock);
  }

  @Override
  public Stream<Void> apply(final List<BlockHeader> headers) {
    if (headers.isEmpty()) {
      return Stream.empty();
    }

    // Validate and track boundaries
    validateAndTrackBoundaries(headers);

    // Save all headers in this batch
    for (final BlockHeader header : headers) {
      storeBlockHeader(header);
    }

    // Track this range as completed
    final long rangeStart = headers.get(0).getNumber(); // highest block in this batch
    final long rangeEnd = headers.get(headers.size() - 1).getNumber(); // lowest block

    completedRangeStarts.add(rangeStart);

    // Update lowest seen block
    lowestSeenBlock.updateAndGet(current -> Math.min(current, rangeEnd));

    // Update total count and log progress
    final long totalSaved = totalHeadersSaved.addAndGet(headers.size());
    logProgress(rangeEnd, totalSaved);

    // Periodically persist progress to enable resume
    if (totalSaved % PERSIST_PROGRESS_INTERVAL == 0) {
      persistProgress(rangeEnd);
    }

    return Stream.empty();
  }

  /**
   * Persists the current download progress to enable resume on restart.
   *
   * @param currentLowestBlock the current lowest block being processed
   */
  private void persistProgress(final long currentLowestBlock) {
    try {
      fastSyncState.setLowestContiguousBlockHeaderDownloaded(currentLowestBlock);
      fastSyncStateStorage.storeState(fastSyncState);
      LOG.debug("Persisted backward header download progress: lowestBlock={}", currentLowestBlock);
    } catch (Exception e) {
      LOG.warn("Failed to persist backward header download progress", e);
      // Don't fail the download if persistence fails
    }
  }

  /**
   * Validates boundaries between batches and tracks hashes for future validation.
   *
   * <p>GetHeadersFromPeerTask already validates internal chain continuity within each batch. We
   * only need to validate the boundary connections between out-of-order batches.
   *
   * @param headers the headers in reverse order [n, n-1, n-2, ...]
   */
  private void validateAndTrackBoundaries(final List<BlockHeader> headers) {
    final BlockHeader highestHeader = headers.get(0); // highest block in this batch
    final BlockHeader lowestHeader = headers.get(headers.size() - 1); // lowest block

    final long highestBlockNumber = highestHeader.getNumber();
    final long lowestBlockNumber = lowestHeader.getNumber();

    // Check if we can validate this range's lower boundary with a previous range
    final Hash expectedHash = highestBlockHashes.get(lowestBlockNumber - 1);
    if (expectedHash != null) {
      // We have the hash of block (lowestBlockNumber - 1), validate connection
      if (!lowestHeader.getParentHash().equals(expectedHash)) {
        throw InvalidBlockException.fromInvalidBlock(
            String.format(
                "Batch boundary validation failed: block %d parentHash %s does not match block %d hash %s",
                lowestBlockNumber,
                lowestHeader.getParentHash(),
                lowestBlockNumber - 1,
                expectedHash),
            lowestHeader);
      }
      // Remove the used hash (cleanup)
      highestBlockHashes.remove(lowestBlockNumber - 1);
      LOG.trace(
          "Validated lower boundary: block {} connects to block {}",
          lowestBlockNumber,
          lowestBlockNumber - 1);
    }

    // Check if we can validate a pending range above this one
    final Hash expectedParentHash = lowestBlockParentHashes.get(highestBlockNumber + 1);
    if (expectedParentHash != null) {
      // A higher range is waiting to validate against this range
      if (!highestHeader.getHash().equals(expectedParentHash)) {
        throw InvalidBlockException.fromInvalidBlock(
            String.format(
                "Batch boundary validation failed: block %d expected parent hash %s does not match block %d hash %s",
                highestBlockNumber + 1,
                expectedParentHash,
                highestBlockNumber,
                highestHeader.getHash()),
            highestHeader);
      }
      // Remove the used parent hash (cleanup)
      lowestBlockParentHashes.remove(highestBlockNumber + 1);
      LOG.trace(
          "Validated upper boundary: block {} connects from block {}",
          highestBlockNumber,
          highestBlockNumber + 1);
    }

    // Store boundaries for future validation (if not already validated)
    if (expectedHash == null) {
      // Store this range's lower boundary for future validation
      lowestBlockParentHashes.put(lowestBlockNumber, lowestHeader.getParentHash());
      LOG.trace(
          "Stored lower boundary: block {} parentHash for future validation", lowestBlockNumber);
    }

    if (expectedParentHash == null) {
      // Store this range's upper boundary for future validation
      highestBlockHashes.put(highestBlockNumber, highestHeader.getHash());
      LOG.trace("Stored upper boundary: block {} hash for future validation", highestBlockNumber);
    }
  }

  private void storeBlockHeader(final BlockHeader header) {
    final Difficulty difficulty = blockchain.calculateTotalDifficulty(header);
    blockchain.unsafeStoreHeader(header, difficulty);
  }

  private void logProgress(final long currentBlock, final long totalSaved) {
    if (currentBlock % LOG_PROGRESS_INTERVAL == 0) {
      final long lowest = lowestSeenBlock.get();
      final long pendingBoundaries =
          (long) lowestBlockParentHashes.size() + highestBlockHashes.size();

      throttledLog(
          LOG::info,
          String.format(
              "Backward header download progress: %d headers saved, lowest block: %d, pending boundaries: %d",
              totalSaved, lowest, pendingBoundaries),
          shouldLog,
          LOG_REPEAT_DELAY_SECONDS);
    }
  }

  /**
   * Get the highest contiguous block number downloaded from the pivot. This is the restart point if
   * the pipeline is interrupted.
   *
   * @return the highest contiguous block number
   */
  public long getHighestContiguousBlock() {
    return highestContiguousBlock.get();
  }

  /**
   * Get the lowest block number seen (may have gaps above it).
   *
   * @return the lowest block number seen
   */
  public long getLowestSeenBlock() {
    return lowestSeenBlock.get();
  }

  /**
   * Get the total number of headers saved.
   *
   * @return the total number of headers saved
   */
  public long getTotalHeadersSaved() {
    return totalHeadersSaved.get();
  }
}
