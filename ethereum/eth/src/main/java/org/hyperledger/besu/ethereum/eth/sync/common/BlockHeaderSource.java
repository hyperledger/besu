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
package org.hyperledger.besu.ethereum.eth.sync.common;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads stored block headers from the blockchain database in forward direction. Used in the second
 * stage to supply headers for bodies and receipts download. Thread-safe for parallel consumption.
 */
public class BlockHeaderSource implements Iterator<List<BlockHeader>> {
  private static final Logger LOG = LoggerFactory.getLogger(BlockHeaderSource.class);

  private static final long CHAIN_HEAD_POLL_MILLIS = 20;

  private final Blockchain blockchain;
  private final long pivotBlockNumber;
  private final int batchSize;
  private final long anchorBlockNumber;
  private final long maxBlocksAheadOfChainHead;

  private final AtomicLong currentBlockNumber;

  /**
   * Creates a new BlockHeaderSource.
   *
   * @param blockchain the blockchain to read headers from
   * @param anchorBlockNumber the block number before the block to start with
   * @param pivotBlockNumber the block number to stop at (inclusive)
   * @param batchSize the number of headers to return per batch
   */
  public BlockHeaderSource(
      final Blockchain blockchain,
      final long anchorBlockNumber,
      final long pivotBlockNumber,
      final int batchSize) {
    this(blockchain, anchorBlockNumber, pivotBlockNumber, batchSize, Long.MAX_VALUE);
  }

  /**
   * Creates a new BlockHeaderSource that does not run further ahead of the chain head than the
   * given number of blocks. Where the blocks are stored as their downloads complete and the chain
   * head follows behind, this bounds how much is stored ahead of a block that keeps failing to
   * download.
   *
   * @param blockchain the blockchain to read headers from
   * @param anchorBlockNumber the block number before the block to start with
   * @param pivotBlockNumber the block number to stop at (inclusive)
   * @param batchSize the number of headers to return per batch
   * @param maxBlocksAheadOfChainHead how far the first block of a batch may be ahead of the chain
   *     head when the batch is handed out
   */
  public BlockHeaderSource(
      final Blockchain blockchain,
      final long anchorBlockNumber,
      final long pivotBlockNumber,
      final int batchSize,
      final long maxBlocksAheadOfChainHead) {
    this.blockchain = blockchain;
    this.pivotBlockNumber = pivotBlockNumber;
    this.batchSize = batchSize;
    this.anchorBlockNumber = anchorBlockNumber;
    this.maxBlocksAheadOfChainHead = maxBlocksAheadOfChainHead;
    this.currentBlockNumber = new AtomicLong(anchorBlockNumber + 1);

    LOG.debug(
        "BlockHeaderSource created: start={}, end={}, batchSize={}",
        anchorBlockNumber,
        pivotBlockNumber,
        batchSize);
  }

  @Override
  public boolean hasNext() {
    return currentBlockNumber.get() <= pivotBlockNumber;
  }

  @Override
  public List<BlockHeader> next() {
    if (currentBlockNumber.get() > pivotBlockNumber) {
      LOG.debug("BlockHeaderSource exhausted at block {}", currentBlockNumber);
      throw new NoSuchElementException(
          "BlockHeaderSource exhausted at block " + currentBlockNumber);
    }

    long start = currentBlockNumber.getAndAdd(batchSize);
    final int actualLength = (int) Math.min(batchSize, pivotBlockNumber - start + 1);
    waitForChainHead(start);

    LOG.trace(
        "BlockHeaderSource reading batch: {} blocks from block number {}", actualLength, start);

    return blockchain.getBlockHeaders(start, actualLength);
  }

  /** Waits until the chain head is close enough for the batch starting at the given block. */
  private void waitForChainHead(final long firstBlockOfBatch) {
    if (maxBlocksAheadOfChainHead == Long.MAX_VALUE) {
      return;
    }
    try {
      // the chain head can be below the anchor until the first batch is on the chain
      while (firstBlockOfBatch - Math.max(anchorBlockNumber, blockchain.getChainHeadBlockNumber())
          > maxBlocksAheadOfChainHead) {
        Thread.sleep(CHAIN_HEAD_POLL_MILLIS);
      }
    } catch (final InterruptedException e) {
      // the pipeline is shutting down, what is handed out now is not processed any more
      Thread.currentThread().interrupt();
    }
  }
}
