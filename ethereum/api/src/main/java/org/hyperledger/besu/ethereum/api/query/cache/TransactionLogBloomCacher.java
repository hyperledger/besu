/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.query.cache;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The type Transaction log bloom cacher. */
public class TransactionLogBloomCacher {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionLogBloomCacher.class);
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";

  /** The constant BLOCKS_PER_BLOOM_CACHE. */
  public static final int BLOCKS_PER_BLOOM_CACHE = 100_000;

  /** The constant BLOOM_BITS_LENGTH. */
  public static final int BLOOM_BITS_LENGTH = 256;

  private static final int EXPECTED_BLOOM_FILE_SIZE = BLOCKS_PER_BLOOM_CACHE * BLOOM_BITS_LENGTH;

  /** The constant CURRENT. */
  public static final String CURRENT = "current";

  private final Map<Long, Boolean> cachedSegments;

  private final Lock submissionLock = new ReentrantLock();

  private final EthScheduler scheduler;
  private final Blockchain blockchain;

  private final Path cacheDir;

  private final CachingStatus cachingStatus = new CachingStatus();

  /**
   * Instantiates a new Transaction log bloom cacher.
   *
   * @param blockchain the blockchain
   * @param cacheDir the cache dir
   * @param scheduler the scheduler
   */
  public TransactionLogBloomCacher(
      final Blockchain blockchain, final Path cacheDir, final EthScheduler scheduler) {
    this.blockchain = blockchain;
    this.cacheDir = cacheDir;
    this.scheduler = scheduler;
    this.cachedSegments = new TreeMap<>();
  }

  /**
   * Gets caching status.
   *
   * @return the caching status
   */
  public CachingStatus getCachingStatus() {
    return cachingStatus;
  }

  /** Cache all. */
  void cacheAll() {
    ensurePreviousSegmentsArePresent(blockchain.getChainHeadBlockNumber(), false);
  }

  private static File calculateCacheFileName(final String name, final Path cacheDir) {
    return cacheDir.resolve("logBloom-" + name + ".cache").toFile();
  }

  private static File calculateCacheFileName(final long blockNumber, final Path cacheDir) {
    return calculateCacheFileName(Long.toString(blockNumber / BLOCKS_PER_BLOOM_CACHE), cacheDir);
  }

  /**
   * Generate log bloom cache caching status.
   *
   * @param start the start
   * @param stop the stop
   * @return the caching status
   */
  public CachingStatus generateLogBloomCache(final long start, final long stop) {
    checkArgument(
        start % BLOCKS_PER_BLOOM_CACHE == 0, "Start block must be at the beginning of a file");

    if (!cachingStatus.isCaching()) {
      try {
        cachingStatus.cachingCount.incrementAndGet();
        LOG.debug(
            "Generating transaction log bloom cache from block {} to block {} in {}",
            start,
            stop,
            cacheDir);
        if (!Files.isDirectory(cacheDir) && !cacheDir.toFile().mkdirs()) {
          LOG.error("Cache directory '{}' does not exist and could not be made.", cacheDir);
          return cachingStatus;
        }
        for (long blockNum = start; blockNum < stop; blockNum += BLOCKS_PER_BLOOM_CACHE) {
          LOG.trace("Caching segment at {}", blockNum);
          final File cacheFile = calculateCacheFileName(blockNum, cacheDir);
          blockchain
              .getBlockHeader(blockNum)
              .ifPresent(
                  blockHeader ->
                      cacheLogsBloomForBlockHeader(
                          blockHeader, Optional.empty(), Optional.of(cacheFile)));
          fillCacheFile(blockNum, blockNum + BLOCKS_PER_BLOOM_CACHE, cacheFile);
        }
      } catch (final Exception e) {
        LOG.error("Unhandled caching exception", e);
      } finally {
        cachingStatus.cachingCount.decrementAndGet();
        LOG.trace("Caching request complete");
      }
    }

    return cachingStatus;
  }

  private void fillCacheFile(final long startBlock, final long stopBlock, final File currentFile)
      throws IOException {
    long blockNum = startBlock;
    try (final OutputStream out = new FileOutputStream(currentFile)) {
      while (blockNum < stopBlock) {
        final Optional<BlockHeader> maybeHeader = blockchain.getBlockHeader(blockNum);
        if (maybeHeader.isEmpty()) {
          break;
        }
        fillCacheFileWithBlock(maybeHeader.get(), out);
        cachingStatus.currentBlock = blockNum;
        blockNum++;
      }
    } catch (final IOException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      throw e;
    }
  }

  /**
   * Cache logs bloom for block header.
   *
   * @param blockHeader the block header
   * @param commonAncestorBlockHeader the common ancestor block header
   * @param reusedCacheFile the reused cache file
   */
  void cacheLogsBloomForBlockHeader(
      final BlockHeader blockHeader,
      final Optional<BlockHeader> commonAncestorBlockHeader,
      final Optional<File> reusedCacheFile) {
    try {
      if (cachingStatus.cachingCount.incrementAndGet() != 1) {
        return;
      }
      final long blockNumber = blockHeader.getNumber();
      LOG.atTrace()
          .setMessage("Caching logs bloom for block {}")
          .addArgument(() -> "0x" + Long.toHexString(blockNumber))
          .log();
      final File cacheFile = reusedCacheFile.orElse(calculateCacheFileName(blockNumber, cacheDir));
      if (cacheFile.exists()) {
        try {
          final Optional<Long> ancestorBlockNumber =
              commonAncestorBlockHeader.map(ProcessableBlockHeader::getNumber);
          if (ancestorBlockNumber.isPresent()) {
            // walk through the blocks from the common ancestor to the received block in order to
            // reload the cache in case of reorg
            for (long number = ancestorBlockNumber.get() + 1;
                number < blockHeader.getNumber();
                number++) {
              final Optional<BlockHeader> ancestorBlockHeader = blockchain.getBlockHeader(number);
              if (ancestorBlockHeader.isPresent()) {
                cacheSingleBlock(ancestorBlockHeader.get(), cacheFile, true);
              }
            }
          }
          cacheSingleBlock(blockHeader, cacheFile, true);
        } catch (final InvalidCacheException e) {
          populateLatestSegment(blockNumber);
        }
      } else {
        populateLatestSegment(blockNumber);
      }
    } catch (final IOException e) {
      if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
        LOG.error(e.getMessage());
        System.exit(0);
      }
      LOG.error("Unhandled caching exception.", e);
    } finally {
      cachingStatus.cachingCount.decrementAndGet();
    }
  }

  private void cacheSingleBlock(
      final BlockHeader blockHeader, final File cacheFile, final boolean isCheckSizeNeeded)
      throws IOException, InvalidCacheException {
    try (final RandomAccessFile writer = new RandomAccessFile(cacheFile, "rw")) {

      final long nbCachedBlocks = cacheFile.length() / BLOOM_BITS_LENGTH;
      final long blockIndex = (blockHeader.getNumber() % BLOCKS_PER_BLOOM_CACHE);
      final long offset = blockIndex * BLOOM_BITS_LENGTH;
      if (isCheckSizeNeeded && blockIndex > nbCachedBlocks) {
        throw new InvalidCacheException();
      }
      writer.seek(offset);
      writer.write(ensureBloomBitsAreCorrectLength(blockHeader.getLogsBloom().toArray()));

      // remove invalid logs when there was a reorg
      final long validCacheSize = offset + BLOOM_BITS_LENGTH;

      if (writer.length() > validCacheSize) {
        writer.setLength(validCacheSize);
      }
    }
  }

  private boolean populateLatestSegment(final long eventBlockNumber) {
    try {
      final File currentFile = calculateCacheFileName(CURRENT, cacheDir);
      final long segmentNumber = eventBlockNumber / BLOCKS_PER_BLOOM_CACHE;
      long blockNumber =
          Math.min((segmentNumber + 1) * BLOCKS_PER_BLOOM_CACHE - 1, eventBlockNumber);
      fillCacheFile(segmentNumber * BLOCKS_PER_BLOOM_CACHE, blockNumber, currentFile);
      while (blockNumber <= eventBlockNumber && (blockNumber % BLOCKS_PER_BLOOM_CACHE != 0)) {
        Optional<BlockHeader> blockHeader = blockchain.getBlockHeader(blockNumber);
        if (blockHeader.isPresent()) {
          cacheSingleBlock(blockHeader.get(), currentFile, false);
        }
        blockNumber++;
      }
      Files.move(
          currentFile.toPath(),
          calculateCacheFileName(blockNumber, cacheDir).toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
      return true;
    } catch (final IOException | InvalidCacheException e) {
      LOG.error("Unhandled caching exception.", e);
    }

    return false;
  }

  /**
   * Remove segments.
   *
   * @param startBlock the start block
   * @param stopBlock the stop block
   */
  public void removeSegments(final Long startBlock, final Long stopBlock) {
    if (!cachingStatus.isCaching()) {
      LOG.info(
          "Deleting transaction log bloom cache from block {} to block {} in {}",
          startBlock,
          stopBlock,
          cacheDir);

      for (long blockNum = startBlock; blockNum <= stopBlock; blockNum += BLOCKS_PER_BLOOM_CACHE) {
        try {
          final long segmentNumber = blockNum / BLOCKS_PER_BLOOM_CACHE;
          final long fromBlock = segmentNumber * BLOCKS_PER_BLOOM_CACHE;
          final File cacheFile = calculateCacheFileName(fromBlock, cacheDir);
          cachedSegments.remove(segmentNumber);
          if (Files.deleteIfExists(cacheFile.toPath())) {
            LOG.info(
                "Deleted transaction log bloom cache file: {}/{}", cacheDir, cacheFile.getName());
          } else {
            LOG.info(
                "Unable to delete transaction log bloom cache file: {}/{}",
                cacheDir,
                cacheFile.getName());
          }
        } catch (final IOException e) {
          if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
            LOG.error(e.getMessage());
            System.exit(0);
          }
          LOG.error(
              String.format("Unhandled exception removing cache for block number %d", blockNum), e);
        }
      }
    }
  }

  /**
   * Ensure previous segments are present.
   *
   * @param blockNumber the block number
   * @param overrideCacheCheck the override cache check
   */
  public void ensurePreviousSegmentsArePresent(
      final long blockNumber, final boolean overrideCacheCheck) {
    if (!cachingStatus.isCaching()) {
      scheduler.scheduleFutureTask(
          () ->
              scheduler.scheduleComputationTask(
                  () -> {
                    long currentSegment = (blockNumber / BLOCKS_PER_BLOOM_CACHE) - 1;
                    while (currentSegment >= 0) {
                      try {
                        if (overrideCacheCheck
                            || !cachedSegments.getOrDefault(currentSegment, false)) {
                          final long startBlock = currentSegment * BLOCKS_PER_BLOOM_CACHE;
                          final File cacheFile = calculateCacheFileName(startBlock, cacheDir);
                          if (overrideCacheCheck
                              || !cacheFile.isFile()
                              || cacheFile.length() != EXPECTED_BLOOM_FILE_SIZE) {
                            generateLogBloomCache(startBlock, startBlock + BLOCKS_PER_BLOOM_CACHE);
                          }
                          cachedSegments.put(currentSegment, true);
                        }
                      } finally {
                        currentSegment--;
                      }
                    }
                    return null;
                  }),
          Duration.ofSeconds(1));
    }
  }

  private void fillCacheFileWithBlock(final BlockHeader blockHeader, final OutputStream fos)
      throws IOException {
    fos.write(ensureBloomBitsAreCorrectLength(blockHeader.getLogsBloom().toArray()));
  }

  private byte[] ensureBloomBitsAreCorrectLength(final byte[] logs) {
    checkNotNull(logs);
    checkState(logs.length == BLOOM_BITS_LENGTH, "BloomBits are not the correct length");
    return logs;
  }

  /**
   * Request caching caching status.
   *
   * @param fromBlock the from block
   * @param toBlock the to block
   * @return the caching status
   */
  public CachingStatus requestCaching(final long fromBlock, final long toBlock) {
    boolean requestAccepted = false;
    try {
      if ((fromBlock < toBlock) && submissionLock.tryLock(100, TimeUnit.MILLISECONDS)) {
        try {
          if (!cachingStatus.isCaching()) {
            requestAccepted = true;
            cachingStatus.startBlock = fromBlock;
            cachingStatus.endBlock = toBlock;
            scheduler.scheduleComputationTask(
                () ->
                    generateLogBloomCache(
                        fromBlock - (fromBlock % BLOCKS_PER_BLOOM_CACHE), toBlock));
          }
        } finally {
          submissionLock.unlock();
        }
      }
    } catch (final InterruptedException e) {
      // ignore
    }
    cachingStatus.requestAccepted = requestAccepted;
    return cachingStatus;
  }

  /**
   * Gets scheduler.
   *
   * @return the scheduler
   */
  EthScheduler getScheduler() {
    return scheduler;
  }

  /**
   * Gets cache dir.
   *
   * @return the cache dir
   */
  Path getCacheDir() {
    return cacheDir;
  }

  /** The type Caching status. */
  public static final class CachingStatus {
    /** The Start block. */
    long startBlock;

    /** The End block. */
    long endBlock;

    /** The Current block. */
    volatile long currentBlock;

    /** The Caching count. */
    AtomicInteger cachingCount = new AtomicInteger(0);

    /** The Request accepted. */
    boolean requestAccepted;

    /** Default constructor. */
    public CachingStatus() {}

    /**
     * Gets start block.
     *
     * @return the start block
     */
    @JsonGetter
    public String getStartBlock() {
      return "0x" + Long.toHexString(startBlock);
    }

    /**
     * Gets end block.
     *
     * @return the end block
     */
    @JsonGetter
    public String getEndBlock() {
      return endBlock == Long.MAX_VALUE ? "latest" : "0x" + Long.toHexString(endBlock);
    }

    /**
     * Gets current block.
     *
     * @return the current block
     */
    @JsonGetter
    public String getCurrentBlock() {
      return "0x" + Long.toHexString(currentBlock);
    }

    /**
     * Is caching boolean.
     *
     * @return the boolean
     */
    @JsonGetter
    public boolean isCaching() {
      return cachingCount.get() > 0;
    }

    /**
     * Is request accepted boolean.
     *
     * @return the boolean
     */
    @JsonGetter
    public boolean isRequestAccepted() {
      return requestAccepted;
    }
  }

  /** The type Invalid cache exception. */
  public static class InvalidCacheException extends Exception {
    /** Default constructor. */
    public InvalidCacheException() {}
  }
}
