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
   * @param stopBlock the lowest header to download
   * @param startBlock the highest header to download
   */
  public BackwardHeaderSource(final int batchSize, final long stopBlock, final long startBlock) {

    this.currentBlock = new AtomicLong(startBlock);
    this.stopBlock = stopBlock;
    this.batchSize = batchSize;

    LOG.info(
        "BackwardHeaderSource starting fresh: startBlock={}, stopBlock={}, batchSize={}",
        startBlock,
        stopBlock,
        batchSize);
  }

  @Override
  public boolean hasNext() {
    return currentBlock.get() >= stopBlock;
  }

  @Override
  public Long next() {
    final long block = currentBlock.getAndUpdate(current -> current - batchSize);

    if (block >= stopBlock) {
      return block;
    }

    LOG.debug("BackwardHeaderSource exhausted at block {} (stopBlock={})", block, stopBlock);
    return null;
  }
}
