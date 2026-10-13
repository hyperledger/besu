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
package org.hyperledger.besu.ethereum.eth.sync.common;

import static org.hyperledger.besu.util.log.LogUtil.throttledLog;

import org.hyperledger.besu.ethereum.chain.MissingBlockBodies;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.SyncBlockWithReceipts;

import java.time.Clock;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores the bodies and receipts of the blocks a snap sync left for later, and keeps track of which
 * of them are still missing.
 *
 * <p>The batches arrive in the order their downloads complete. Each one is stored when it arrives.
 * The range of missing blocks recorded with the chain only shrinks over blocks that have every
 * block before them stored, so a download that is interrupted continues from there and downloads
 * what was stored ahead of it again.
 */
public class StoreMissingBodiesStep implements Consumer<List<SyncBlockWithReceipts>> {
  private static final Logger LOG = LoggerFactory.getLogger(StoreMissingBodiesStep.class);
  private static final int PRINT_DELAY_SECONDS = 30;

  /** How long the recorded range may lag behind what is stored. */
  static final long RECORD_INTERVAL_MILLIS = 5_000;

  private final MutableBlockchain blockchain;
  private final boolean transactionIndexingEnabled;
  private final Difficulty totalDifficulty;
  private final long firstBlock;
  private final long lastBlock;
  private final Clock clock;
  private final AtomicBoolean isTimeToLog = new AtomicBoolean(true);
  // the last block of the batches that are stored but follow a batch that is not, by first block
  private final NavigableMap<Long, Long> storedAhead = new TreeMap<>();
  // the first block that is not stored yet
  private volatile long nextBlockNumber;
  private long lastRecordMillis;

  /**
   * @param blockchain the blockchain the blocks belong to
   * @param missingBlockBodies the blocks to store
   * @param totalDifficulty the total difficulty of every one of the blocks
   * @param transactionIndexingEnabled whether to index the transactions of the blocks
   * @param clock tells when the range was last recorded
   */
  public StoreMissingBodiesStep(
      final MutableBlockchain blockchain,
      final MissingBlockBodies missingBlockBodies,
      final Difficulty totalDifficulty,
      final boolean transactionIndexingEnabled,
      final Clock clock) {
    this.blockchain = blockchain;
    this.totalDifficulty = totalDifficulty;
    this.transactionIndexingEnabled = transactionIndexingEnabled;
    this.firstBlock = missingBlockBodies.firstBlock();
    this.lastBlock = missingBlockBodies.lastBlock();
    this.clock = clock;
    this.nextBlockNumber = missingBlockBodies.firstBlock();
    this.lastRecordMillis = clock.millis();
  }

  @Override
  public void accept(final List<SyncBlockWithReceipts> blocksWithReceipts) {
    blockchain.unsafeStoreSyncBodiesAndReceipts(
        blocksWithReceipts, transactionIndexingEnabled, totalDifficulty);
    storedAhead.put(
        blocksWithReceipts.getFirst().getNumber(), blocksWithReceipts.getLast().getNumber());

    final long previousNextBlockNumber = nextBlockNumber;
    while (!storedAhead.isEmpty() && storedAhead.firstKey() <= nextBlockNumber) {
      nextBlockNumber = Math.max(nextBlockNumber, storedAhead.pollFirstEntry().getValue() + 1);
    }
    if (nextBlockNumber == previousNextBlockNumber) {
      return;
    }

    if (nextBlockNumber > lastBlock) {
      blockchain.unsafeSetMissingBlockBodies(Optional.empty());
      LOG.info(
          "Chain history is complete: stored the bodies and receipts of the blocks up to {}",
          lastBlock);
      return;
    }
    final long now = clock.millis();
    if (now - lastRecordMillis >= RECORD_INTERVAL_MILLIS) {
      lastRecordMillis = now;
      blockchain.unsafeSetMissingBlockBodies(
          Optional.of(new MissingBlockBodies(nextBlockNumber, lastBlock)));
    }
    if (isTimeToLog.get()) {
      throttledLog(
          LOG::info,
          // of the blocks that were missing when this download started: on a chain that is synced
          // from a checkpoint the first of them is far from block 1
          String.format(
              "Chain history download progress: %s of %s (%s%%)",
              nextBlockNumber - 1,
              lastBlock,
              ImportSyncBlocksStep.getBlocksPercent(
                  nextBlockNumber - firstBlock, lastBlock - firstBlock + 1)),
          isTimeToLog,
          PRINT_DELAY_SECONDS);
    }
  }

  /**
   * The last block that is stored with every block before it.
   *
   * @return the number of the block before the first one that is still missing
   */
  public long lastBlockStoredInOrder() {
    return nextBlockNumber - 1;
  }
}
