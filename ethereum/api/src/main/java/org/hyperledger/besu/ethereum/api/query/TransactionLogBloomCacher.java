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
 *
 */

package org.hyperledger.besu.ethereum.api.query;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionLogBloomCacher {

  private static final Logger LOG = LogManager.getLogger();

  public static final int BLOCKS_PER_BLOOM_CACHE = 100_000;
  private static final int BLOOM_BITS_LENGTH = 256;
  private final Map<Long, Boolean> cachedSegments;

  private final Lock submissionLock = new ReentrantLock();

  private final EthScheduler scheduler;
  private final Blockchain blockchain;

  private final Path cacheDir;

  private final CachingStatus cachingStatus = new CachingStatus();

  public TransactionLogBloomCacher(
      final Blockchain blockchain, final Path cacheDir, final EthScheduler scheduler) {
    this.blockchain = blockchain;
    this.cacheDir = cacheDir;
    this.scheduler = scheduler;
    this.cachedSegments = new TreeMap<>();
  }

  public void cacheAll() {
    ensurePreviousSegmentsArePresent(blockchain.getChainHeadBlockNumber());
  }

  private static File calculateCacheFileName(final String name, final Path cacheDir) {
    return cacheDir.resolve("logBloom-" + name + ".cache").toFile();
  }

  private static File calculateCacheFileName(final long blockNumber, final Path cacheDir) {
    return calculateCacheFileName(Long.toString(blockNumber / BLOCKS_PER_BLOOM_CACHE), cacheDir);
  }

  public CachingStatus generateLogBloomCache(final long start, final long stop) {
    checkArgument(
        start % BLOCKS_PER_BLOOM_CACHE == 0, "Start block must be at the beginning of a file");
    try {
      cachingStatus.caching = true;
      LOG.info(
          "Generating transaction log bloom cache from block {} to block {} in {}",
          start,
          stop,
          cacheDir);
      if (!Files.isDirectory(cacheDir) && !cacheDir.toFile().mkdirs()) {
        LOG.error("Cache directory '{}' does not exist and could not be made.", cacheDir);
        return cachingStatus;
      }
      for (long blockNum = start; blockNum < stop; blockNum += BLOCKS_PER_BLOOM_CACHE) {
        LOG.info("Caching segment at {}", blockNum);
        final File cacheFile = calculateCacheFileName(blockNum, cacheDir);
        blockchain
            .getBlockHeader(blockNum)
            .ifPresent(
                blockHeader ->
                    cacheLogsBloomForBlockHeader(blockHeader, Optional.of(cacheFile), false));
        try (final FileOutputStream fos = new FileOutputStream(cacheFile)) {
          fillCacheFile(blockNum, blockNum + BLOCKS_PER_BLOOM_CACHE, fos);
        }
      }
    } catch (final Exception e) {
      LOG.error("Unhandled caching exception", e);
    } finally {
      cachingStatus.caching = false;
      LOG.info("Caching request complete");
    }
    return cachingStatus;
  }

  private void fillCacheFile(
      final long startBlock, final long stopBlock, final FileOutputStream fos) throws IOException {
    long blockNum = startBlock;
    while (blockNum < stopBlock) {
      final Optional<BlockHeader> maybeHeader = blockchain.getBlockHeader(blockNum);
      if (maybeHeader.isEmpty()) {
        break;
      }
      fillCacheFileWithBlock(maybeHeader.get(), fos);
      cachingStatus.currentBlock = blockNum;
      blockNum++;
    }
  }

  public void cacheLogsBloomForBlockHeader(
      final BlockHeader blockHeader,
      final Optional<File> reusedCacheFile,
      final boolean ensureChecks) {
    try {
      final long blockNumber = blockHeader.getNumber();
      LOG.debug("Caching logs bloom for block {}.", "0x" + Long.toHexString(blockNumber));
      if (ensureChecks) {
        ensurePreviousSegmentsArePresent(blockNumber);
      }
      final File cacheFile = reusedCacheFile.orElse(calculateCacheFileName(blockNumber, cacheDir));
      if (!cacheFile.exists()) {
        Files.createFile(cacheFile.toPath());
      }
      try (RandomAccessFile writer = new RandomAccessFile(cacheFile, "rw")) {
        final long offset = (blockNumber / BLOCKS_PER_BLOOM_CACHE) * BLOOM_BITS_LENGTH;
        writer.seek(offset);
        writer.write(ensureBloomBitsAreCorrectLength(blockHeader.getLogsBloom().toArray()));
      }
    } catch (IOException e) {
      LOG.error("Unhandled caching exception.", e);
    }
  }

  private void ensurePreviousSegmentsArePresent(final long blockNumber) {
    if (!cachingStatus.isCaching()) {
      scheduler.scheduleFutureTask(
          () -> {
            long currentSegment = (blockNumber / BLOCKS_PER_BLOOM_CACHE) - 1;
            while (currentSegment > 0) {
              try {
                if (!cachedSegments.getOrDefault(currentSegment, false)) {
                  final long startBlock = currentSegment * BLOCKS_PER_BLOOM_CACHE;
                  generateLogBloomCache(startBlock, startBlock + BLOCKS_PER_BLOOM_CACHE);
                  cachedSegments.put(currentSegment, true);
                }
              } finally {
                currentSegment--;
              }
            }
          },
          Duration.ofSeconds(1));
    }
  }

  private void fillCacheFileWithBlock(final BlockHeader blockHeader, final FileOutputStream fos)
      throws IOException {
    fos.write(ensureBloomBitsAreCorrectLength(blockHeader.getLogsBloom().toArray()));
  }

  private byte[] ensureBloomBitsAreCorrectLength(final byte[] logs) {
    checkNotNull(logs);
    checkState(logs.length == BLOOM_BITS_LENGTH, "BloomBits are not the correct length");
    return logs;
  }

  public CachingStatus requestCaching(final long fromBlock, final long toBlock) {
    boolean requestAccepted = false;
    try {
      if ((fromBlock < toBlock) && submissionLock.tryLock(100, TimeUnit.MILLISECONDS)) {
        try {
          if (!cachingStatus.caching) {
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

  public EthScheduler getScheduler() {
    return scheduler;
  }

  Path getCacheDir() {
    return cacheDir;
  }

  public static final class CachingStatus {
    long startBlock;
    long endBlock;
    volatile long currentBlock;
    volatile boolean caching;
    boolean requestAccepted;

    @JsonGetter
    public String getStartBlock() {
      return "0x" + Long.toHexString(startBlock);
    }

    @JsonGetter
    public String getEndBlock() {
      return endBlock == Long.MAX_VALUE ? "latest" : "0x" + Long.toHexString(endBlock);
    }

    @JsonGetter
    public String getCurrentBlock() {
      return "0x" + Long.toHexString(currentBlock);
    }

    @JsonGetter
    public boolean isCaching() {
      return caching;
    }

    @JsonGetter
    public boolean isRequestAccepted() {
      return requestAccepted;
    }
  }
}
