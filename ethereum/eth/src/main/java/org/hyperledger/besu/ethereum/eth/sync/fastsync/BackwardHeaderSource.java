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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates block numbers in descending order for backward header sync. Thread-safe for parallel
 * consumption.
 */
public class BackwardHeaderSource implements Iterator<Long> {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardHeaderSource.class);

  private final AtomicLong currentBlock;
  private final int batchSize;

  /**
   * Creates a new BackwardHeaderSource with resume capability.
   *
   * @param pivotBlockHash the pivot block hash to start from
   * @param batchSize the number of blocks in each batch
   * @param ethContext the Ethereum context for fetching headers
   * @param protocolSchedule the protocol schedule
   * @param metricsSystem the metrics system
   * @param blockchain the blockchain to check for already downloaded headers
   * @param fastSyncState the fast sync state to determine resume point
   */
  public BackwardHeaderSource(
      final Hash pivotBlockHash,
      final int batchSize,
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final Blockchain blockchain,
      final FastSyncState fastSyncState) {

    // Download the pivot block header and use its number as the starting point
    final long pivotBlockNumber =
        fetchPivotBlockNumber(pivotBlockHash, ethContext, protocolSchedule, metricsSystem);

    // Determine where to start/resume from
    final long startingBlock = determineStartingBlock(pivotBlockNumber, blockchain, fastSyncState);

    this.currentBlock = new AtomicLong(startingBlock);
    this.batchSize = batchSize;

    if (startingBlock < pivotBlockNumber) {
      LOG.info(
          "BackwardHeaderSource resuming: pivotHash={}, pivotNumber={}, resumeFrom={}, batchSize={}",
          pivotBlockHash,
          pivotBlockNumber,
          startingBlock,
          batchSize);
    } else {
      LOG.info(
          "BackwardHeaderSource starting fresh: pivotHash={}, pivotNumber={}, batchSize={}",
          pivotBlockHash,
          pivotBlockNumber,
          batchSize);
    }
  }

  /**
   * Determines the starting block for backward header download, checking for resume capability.
   *
   * @param pivotBlockNumber the pivot block number
   * @param blockchain the blockchain to check for existing headers
   * @param fastSyncState the fast sync state containing resume information
   * @return the block number to start downloading from
   */
  private long determineStartingBlock(
      final long pivotBlockNumber,
      final Blockchain blockchain,
      final FastSyncState fastSyncState) {

    // Check if we have persisted progress
    if (fastSyncState.getLowestContiguousBlockHeaderDownloaded().isPresent()) {
      final long lowestDownloaded = fastSyncState.getLowestContiguousBlockHeaderDownloaded().getAsLong();

      // Verify the persisted state matches what's in the database
      if (blockchain.getBlockHeader(lowestDownloaded).isPresent()) {
        LOG.info(
            "Resuming from persisted state: lowest contiguous block = {}", lowestDownloaded);
        return lowestDownloaded;
      } else {
        LOG.warn(
            "Persisted state indicates block {} downloaded, but not found in DB. Scanning database...",
            lowestDownloaded);
      }
    }

    // Fall back to scanning the database to find the resume point
    return findLowestContiguousBlock(pivotBlockNumber, blockchain);
  }

  /**
   * Scans the blockchain database to find the lowest contiguous block downloaded from the pivot.
   *
   * @param pivotBlockNumber the pivot block number
   * @param blockchain the blockchain to scan
   * @return the lowest contiguous block number, or pivotBlockNumber if nothing downloaded yet
   */
  private long findLowestContiguousBlock(final long pivotBlockNumber, final Blockchain blockchain) {
    long currentBlock = pivotBlockNumber;

    // Walk backward in steps of batchSize until we find a missing header
    while (currentBlock >= 0) {
      if (blockchain.getBlockHeader(currentBlock).isEmpty()) {
        // Found a gap - resume from the block after this gap
        final long resumeFrom = Math.min(currentBlock + batchSize, pivotBlockNumber);
        LOG.info(
            "Database scan found gap at block {}. Resuming from block {}",
            currentBlock,
            resumeFrom);
        return resumeFrom;
      }
      currentBlock -= batchSize;
    }

    // All blocks down to genesis are stored - nothing to download
    LOG.info("All headers from pivot {} to genesis already downloaded", pivotBlockNumber);
    return -1; // Signal that download is complete
  }

  private long fetchPivotBlockNumber(
      final Hash pivotBlockHash,
      final EthContext ethContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem) {
    LOG.trace("Fetching pivot block header by hash: {}", pivotBlockHash);

    try {
      final CompletableFuture<AbstractPeerTask.PeerTaskResult<List<BlockHeader>>> future =
          GetHeadersFromPeerByHashTask.forSingleHash(
                  protocolSchedule, ethContext, pivotBlockHash, 0L, metricsSystem)
              .run();

      final AbstractPeerTask.PeerTaskResult<List<BlockHeader>> result = future.join();
      final List<BlockHeader> headers = result.getResult();

      if (headers.isEmpty()) {
        throw new IllegalStateException(
            "Failed to fetch pivot block header for safe hash: " + pivotBlockHash);
      }

      final BlockHeader pivotHeader = headers.getFirst();

      // just make sure that we got the right block header as the hash is what we trust
      if (!pivotHeader.getHash().equals(pivotBlockHash)) {
          throw new IllegalStateException(
            "Hash of retrieved block header does not match pivot block header hash: " + pivotBlockHash);
      }

      final long blockNumber = pivotHeader.getNumber();

      LOG.info(
          "Successfully fetched pivot block header: hash={}, number={}",
          pivotBlockHash,
          blockNumber);

      return blockNumber;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to fetch pivot block header for hash: " + pivotBlockHash, e);
    }
  }

  @Override
  public boolean hasNext() {
    return currentBlock.get() >= 0;
  }

  @Override
  public Long next() {
    final long block =
        currentBlock.getAndUpdate(
            current -> {
              final long next = current - batchSize;
              return next >= 0 ? next : -1;
            });

    if (block >= 0) {
      LOG.trace("BackwardHeaderSource generated block number: {}", block);
      return block;
    }

    return null;
  }
}
