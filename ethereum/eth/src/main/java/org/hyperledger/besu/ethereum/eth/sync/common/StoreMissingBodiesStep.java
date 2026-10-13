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
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;

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
 *
 * <p>The node follows the chain while this runs, and the blocks it imports go to the same storage.
 * So a batch is only stored once the storage has written out what it was given before, and after
 * waiting for that this step stays idle for as long again: stored as fast as they arrive, the
 * batches fill the write buffers faster than they are flushed, which stops every write to the
 * storage, and they leave the disk no room for the reads a new block needs.
 *
 * <p>The bodies and receipts are written past the write-ahead log of the storage, which would
 * otherwise write all of them to disk twice. So before the recorded range shrinks over them, the
 * storage is made to write them out: what a crash loses is never recorded as stored, and is
 * downloaded again.
 */
public class StoreMissingBodiesStep implements Consumer<List<SyncBlockWithReceipts>> {
  private static final Logger LOG = LoggerFactory.getLogger(StoreMissingBodiesStep.class);
  private static final int PRINT_DELAY_SECONDS = 30;

  /** How long the recorded range may lag behind what is stored. */
  static final long RECORD_INTERVAL_MILLIS = 5_000;

  /** How often the storage is asked whether it has written out what it was given. */
  static final long FLUSH_POLL_MILLIS = 10;

  /** A batch is stored after this long even if the storage still has a buffer to write out. */
  static final long MAX_FLUSH_WAIT_MILLIS = 30_000;

  /** Waits without holding anything, for the time given in milliseconds. */
  @FunctionalInterface
  interface Pause {
    void forMillis(long millis) throws InterruptedException;
  }

  private final MutableBlockchain blockchain;
  private final boolean transactionIndexingEnabled;
  private final Difficulty totalDifficulty;
  private final long firstBlock;
  private final long lastBlock;
  private final Clock clock;
  private final Pause pause;
  private final long startMillis;
  private final AtomicBoolean isTimeToLog = new AtomicBoolean(true);
  // the last block of the batches that are stored but follow a batch that is not, by first block
  private final NavigableMap<Long, Long> storedAhead = new TreeMap<>();
  // the first block that is not stored yet
  private volatile long nextBlockNumber;
  private long lastRecordMillis;
  // how long this step waited for the storage and stayed idle after that
  private long heldBackMillis;
  // the most bytes of bodies and receipts to store per second, 0 for no limit
  private final long maxBytesPerSecond;
  // when the bytes stored so far are paid for at that rate
  private long rateLimitedUntilMillis;

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
    this(blockchain, missingBlockBodies, totalDifficulty, transactionIndexingEnabled, 0, clock);
  }

  /**
   * @param blockchain the blockchain the blocks belong to
   * @param missingBlockBodies the blocks to store
   * @param totalDifficulty the total difficulty of every one of the blocks
   * @param transactionIndexingEnabled whether to index the transactions of the blocks
   * @param maxMegabytesPerSecond the most megabytes of bodies and receipts to store per second, 0
   *     for no limit
   * @param clock tells when the range was last recorded
   */
  public StoreMissingBodiesStep(
      final MutableBlockchain blockchain,
      final MissingBlockBodies missingBlockBodies,
      final Difficulty totalDifficulty,
      final boolean transactionIndexingEnabled,
      final int maxMegabytesPerSecond,
      final Clock clock) {
    this(
        blockchain,
        missingBlockBodies,
        totalDifficulty,
        transactionIndexingEnabled,
        maxMegabytesPerSecond,
        clock,
        Thread::sleep);
  }

  StoreMissingBodiesStep(
      final MutableBlockchain blockchain,
      final MissingBlockBodies missingBlockBodies,
      final Difficulty totalDifficulty,
      final boolean transactionIndexingEnabled,
      final int maxMegabytesPerSecond,
      final Clock clock,
      final Pause pause) {
    this.blockchain = blockchain;
    this.maxBytesPerSecond = Math.max(0, maxMegabytesPerSecond) * 1_000_000L;
    this.totalDifficulty = totalDifficulty;
    this.transactionIndexingEnabled = transactionIndexingEnabled;
    this.firstBlock = missingBlockBodies.firstBlock();
    this.lastBlock = missingBlockBodies.lastBlock();
    this.clock = clock;
    this.pause = pause;
    this.startMillis = clock.millis();
    this.nextBlockNumber = missingBlockBodies.firstBlock();
    this.lastRecordMillis = clock.millis();
    this.rateLimitedUntilMillis = clock.millis();
  }

  @Override
  public void accept(final List<SyncBlockWithReceipts> blocksWithReceipts) {
    waitForTheRateLimit();
    waitForTheStorage();
    blockchain.unsafeStoreSyncBodiesAndReceipts(
        blocksWithReceipts, transactionIndexingEnabled, totalDifficulty);
    payForTheRateLimit(blocksWithReceipts);
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
      blockchain.unsafePersistSyncBodiesAndReceipts();
      blockchain.unsafeSetMissingBlockBodies(Optional.empty());
      LOG.info(
          "Chain history is complete: stored the bodies and receipts of the blocks up to {}",
          lastBlock);
      return;
    }
    final long now = clock.millis();
    if (now - lastRecordMillis >= RECORD_INTERVAL_MILLIS) {
      lastRecordMillis = now;
      blockchain.unsafePersistSyncBodiesAndReceipts();
      blockchain.unsafeSetMissingBlockBodies(
          Optional.of(new MissingBlockBodies(nextBlockNumber, lastBlock)));
    }
    if (isTimeToLog.get()) {
      throttledLog(
          LOG::info,
          // of the blocks that were missing when this download started: on a chain that is synced
          // from a checkpoint the first of them is far from block 1
          String.format(
              "Chain history download progress: %s of %s (%s%%), held back %s%% of the time",
              nextBlockNumber - 1,
              lastBlock,
              ImportSyncBlocksStep.getBlocksPercent(
                  nextBlockNumber - firstBlock, lastBlock - firstBlock + 1),
              100 * heldBackMillis / Math.max(1, now - startMillis)),
          isTimeToLog,
          PRINT_DELAY_SECONDS);
    }
  }

  /** Waits until what was stored before is paid for at the rate limit. */
  private void waitForTheRateLimit() {
    if (maxBytesPerSecond == 0) {
      return;
    }
    final long waitMillis = rateLimitedUntilMillis - clock.millis();
    if (waitMillis > 0) {
      try {
        pause.forMillis(waitMillis);
        heldBackMillis += waitMillis;
      } catch (final InterruptedException e) {
        // the download is being stopped: store what is at hand and let the pipeline end
        Thread.currentThread().interrupt();
      }
    }
  }

  private void payForTheRateLimit(final List<SyncBlockWithReceipts> blocksWithReceipts) {
    if (maxBytesPerSecond == 0) {
      return;
    }
    long bytes = 0;
    for (final SyncBlockWithReceipts blockWithReceipts : blocksWithReceipts) {
      bytes += blockWithReceipts.getBlock().getBody().getRlp().size();
      for (final SyncTransactionReceipt receipt : blockWithReceipts.getReceipts()) {
        bytes += receipt.getRlpBytes().size();
      }
    }
    rateLimitedUntilMillis =
        Math.max(rateLimitedUntilMillis, clock.millis()) + bytes * 1000 / maxBytesPerSecond;
  }

  /**
   * Waits until the storage has no write buffer left to flush, and then as long again, so that the
   * storage is busy with the history for half of the time at most.
   */
  private void waitForTheStorage() {
    long waitedMillis = 0;
    try {
      while (waitedMillis < MAX_FLUSH_WAIT_MILLIS && blockchain.isStorageWaitingForFlush()) {
        pause.forMillis(FLUSH_POLL_MILLIS);
        waitedMillis += FLUSH_POLL_MILLIS;
      }
      if (waitedMillis > 0) {
        heldBackMillis += waitedMillis;
        pause.forMillis(waitedMillis);
        heldBackMillis += waitedMillis;
      }
    } catch (final InterruptedException e) {
      // the download is being stopped: store what is at hand and let the pipeline end
      Thread.currentThread().interrupt();
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
