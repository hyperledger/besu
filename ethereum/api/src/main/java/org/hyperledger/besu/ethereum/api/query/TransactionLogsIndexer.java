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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionLogsIndexer {

  private static final Logger LOG = LogManager.getLogger();

  public static final int BLOCKS_PER_BLOOM_CACHE = 100_000;
  public static final String PENDING = "pending";

  private final Lock submissionLock = new ReentrantLock();
  private final EthScheduler scheduler;
  private final Blockchain blockchain;
  private final Path cacheDir;

  private IndexingStatus indexingStatus = new IndexingStatus();

  public TransactionLogsIndexer(
      final Blockchain blockchain, final Path cacheDir, final EthScheduler scheduler) {
    this.blockchain = blockchain;
    this.cacheDir = cacheDir;
    this.scheduler = scheduler;
  }

  private static File calculateCacheFileName(final String name, final Path cacheDir) {
    return cacheDir.resolve("logBloom-" + name + ".index").toFile();
  }

  private static File calculateCacheFileName(final long blockNumber, final Path cacheDir) {
    return calculateCacheFileName(Long.toString(blockNumber / BLOCKS_PER_BLOOM_CACHE), cacheDir);
  }

  public IndexingStatus generateLogBloomCache(final long start, final long stop) {
    checkArgument(
        start % BLOCKS_PER_BLOOM_CACHE == 0, "Start block must be at the beginning of a file");
    try {
      indexingStatus.indexing = true;
      LOG.info(
          "Generating transaction log indexes from block {} to block {} in {}",
          start,
          stop,
          cacheDir);
      if (!Files.isDirectory(cacheDir) && !cacheDir.toFile().mkdirs()) {
        LOG.error("Cache directory '{}' does not exist and could not be made.", cacheDir);
        return indexingStatus;
      }

      final File pendingFile = calculateCacheFileName(PENDING, cacheDir);
      for (long blockNum = start; blockNum < stop; blockNum += BLOCKS_PER_BLOOM_CACHE) {
        LOG.info("Indexing segment at {}", blockNum);
        try (final FileOutputStream fos = new FileOutputStream(pendingFile)) {
          final long blockCount = fillCacheFile(blockNum, blockNum + BLOCKS_PER_BLOOM_CACHE, fos);
          if (blockCount == BLOCKS_PER_BLOOM_CACHE) {
            Files.move(
                pendingFile.toPath(),
                calculateCacheFileName(blockNum, cacheDir).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
          } else {
            LOG.info("Partial segment at {}, only {} blocks cached", blockNum, blockCount);
            break;
          }
        }
      }
    } catch (final Exception e) {
      LOG.error("Unhandled indexing exception", e);
    } finally {
      indexingStatus.indexing = false;
      LOG.info("Indexing request complete");
    }
    return indexingStatus;
  }

  private long fillCacheFile(
      final long startBlock, final long stopBlock, final FileOutputStream fos) throws IOException {
    long blockNum = startBlock;
    while (blockNum < stopBlock) {
      final Optional<BlockHeader> maybeHeader = blockchain.getBlockHeader(blockNum);
      if (maybeHeader.isEmpty()) {
        break;
      }
      final byte[] logs = maybeHeader.get().getLogsBloom().getByteArray();
      checkNotNull(logs);
      checkState(logs.length == 256, "BloomBits are not the correct length");
      fos.write(logs);
      indexingStatus.currentBlock = blockNum;
      blockNum++;
    }
    return blockNum - startBlock;
  }

  public IndexingStatus requestIndexing(final long fromBlock, final long toBlock) {
    boolean requestAccepted = false;
    try {
      if ((fromBlock < toBlock) && submissionLock.tryLock(100, TimeUnit.MILLISECONDS)) {
        try {
          if (!indexingStatus.indexing) {
            requestAccepted = true;
            indexingStatus.startBlock = fromBlock;
            indexingStatus.endBlock = toBlock;
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
    indexingStatus.requestAccepted = requestAccepted;
    return indexingStatus;
  }

  public static final class IndexingStatus {
    long startBlock;
    long endBlock;
    volatile long currentBlock;
    volatile boolean indexing;
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
    public boolean isIndexing() {
      return indexing;
    }

    @JsonGetter
    public boolean isRequestAccepted() {
      return requestAccepted;
    }
  }
}
