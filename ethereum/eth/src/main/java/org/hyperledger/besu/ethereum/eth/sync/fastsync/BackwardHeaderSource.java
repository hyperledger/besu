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

import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.util.Iterator;
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
  private final long stopBlock;

  /**
   * Creates a new BackwardHeaderSource with resume capability using ChainSyncState.
   *
   * @param batchSize the number of blocks in each batch
   * @param blockchain the blockchain to check for already downloaded headers
   * @param chainSyncState the chain sync state containing pivot and progress
   */
  public BackwardHeaderSource(
      final int batchSize, final Blockchain blockchain, final ChainSyncState chainSyncState) {

    final long pivotBlockNumber = chainSyncState.getPivotBlockNumber();
    final long stopBlock = chainSyncState.getHeaderDownloadStopBlock();

    // Determine where to start/resume from
    final long startingBlock = determineStartingBlock(pivotBlockNumber, blockchain);

    this.currentBlock = new AtomicLong(startingBlock - 1);
    this.batchSize = batchSize;
    this.stopBlock = stopBlock;

    if (startingBlock < pivotBlockNumber) {
      LOG.info(
          "BackwardHeaderSource resuming: pivot={}, stopBlock={}, resumeFrom={}, batchSize={}",
          pivotBlockNumber,
          stopBlock,
          startingBlock,
          batchSize);
    } else {
      LOG.info(
          "BackwardHeaderSource starting fresh: pivot={}, stopBlock={}, batchSize={}",
          pivotBlockNumber,
          stopBlock,
          batchSize);
    }
  }

  /**
   * Scans the blockchain database to find the lowest contiguous block downloaded from the pivot.
   *
   * @param pivotBlockNumber the pivot block number
   * @param blockchain the blockchain to scan
   * @return the lowest contiguous block number, or pivotBlockNumber if nothing downloaded yet
   */
  private long determineStartingBlock(final long pivotBlockNumber, final Blockchain blockchain) {
    long currentBlock = pivotBlockNumber;

    // Walk backward in steps of batchSize until we find a missing header
    while (currentBlock >= stopBlock) {
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

  @Override
  public boolean hasNext() {
    return currentBlock.get() > stopBlock;
  }

  @Override
  public Long next() {
    final long block =
        currentBlock.getAndUpdate(
            current -> {
              final long next = current - batchSize;
              return next >= stopBlock ? next : stopBlock - 1;
            });

    if (block >= stopBlock) {
      return block;
    }

    LOG.debug("BackwardHeaderSource exhausted at block {} (stopBlock={})", block, stopBlock);
    return null;
  }
}
