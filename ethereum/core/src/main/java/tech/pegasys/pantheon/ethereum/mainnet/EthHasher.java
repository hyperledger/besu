/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.mainnet;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.primitives.Ints;

public interface EthHasher {

  /**
   * @param buffer At least 64 bytes long buffer to store EthHash result in
   * @param nonce Block Nonce
   * @param number Block Number
   * @param headerHash Block Header (without mix digest and nonce) Hash
   */
  void hash(byte[] buffer, long nonce, long number, byte[] headerHash);

  final class Light implements EthHasher {

    private static final EthHashCacheFactory cacheFactory = new EthHashCacheFactory();

    @Override
    public void hash(
        final byte[] buffer, final long nonce, final long number, final byte[] headerHash) {
      final EthHashCacheFactory.EthHashDescriptor cache = cacheFactory.ethHashCacheFor(number);
      final byte[] hash =
          EthHash.hashimotoLight(cache.getDatasetSize(), cache.getCache(), headerHash, nonce);
      System.arraycopy(hash, 0, buffer, 0, hash.length);
    }
  }

  final class Full implements EthHasher, Closeable {

    private static final int HASHERS = Runtime.getRuntime().availableProcessors();

    private long epoch = -1L;

    private long datasetSize;

    private final RandomAccessFile cacheFile;

    private final ExecutorService hashers = Executors.newFixedThreadPool(HASHERS);

    public Full(final Path cacheFile) throws IOException {
      this.cacheFile = new RandomAccessFile(cacheFile.toFile(), "rw");
      datasetSize = this.cacheFile.length();
    }

    @Override
    public void hash(
        final byte[] buffer, final long nonce, final long number, final byte[] headerHash) {
      final long newEpoch = EthHash.epoch(number);
      if (epoch != newEpoch) {
        updateCache(number, newEpoch);
      }
      final byte[] hash =
          EthHash.hashimoto(
              headerHash,
              datasetSize,
              nonce,
              (bytes, integer) -> {
                try {
                  cacheFile.seek(integer * EthHash.HASH_BYTES);
                  cacheFile.readFully(bytes);
                } catch (final IOException ex) {
                  throw new IllegalStateException(ex);
                }
              });
      System.arraycopy(hash, 0, buffer, 0, hash.length);
    }

    private void updateCache(final long number, final long newEpoch) {
      final int[] cache = EthHash.mkCache(Ints.checkedCast(EthHash.cacheSize(newEpoch)), number);
      epoch = newEpoch;
      final long newDatasetSize = EthHash.datasetSize(epoch);
      if (newDatasetSize != datasetSize) {
        datasetSize = newDatasetSize;
        final CountDownLatch doneLatch = new CountDownLatch(HASHERS);
        final int upperBound = Ints.checkedCast(datasetSize / EthHash.HASH_BYTES);
        final int partitionSize = upperBound / HASHERS;
        for (int partition = 0; partition < HASHERS; ++partition) {
          hashers.execute(
              new EthHasher.Full.HasherTask(
                  partition * partitionSize,
                  partition == HASHERS - 1 ? upperBound : (partition + 1) * partitionSize,
                  cache,
                  doneLatch,
                  cacheFile));
        }
        try {
          doneLatch.await();
        } catch (final InterruptedException ex) {
          throw new IllegalStateException(ex);
        }
      }
    }

    @Override
    public void close() throws IOException {
      cacheFile.close();
      hashers.shutdownNow();
    }

    private static final class HasherTask implements Runnable {

      private static final int DISK_BATCH_SIZE = 256;

      private final int start;
      private final int end;
      private final int[] cache;
      private final CountDownLatch doneLatch;
      private final RandomAccessFile cacheFile;

      HasherTask(
          final int start,
          final int upperBound,
          final int[] cache,
          final CountDownLatch doneLatch,
          final RandomAccessFile cacheFile) {
        this.end = upperBound;
        this.cache = cache;
        this.start = start;
        this.doneLatch = doneLatch;
        this.cacheFile = cacheFile;
      }

      @Override
      public void run() {
        try {
          final byte[] itemBuffer = new byte[EthHash.HASH_BYTES];
          final byte[] writeBuffer = new byte[EthHash.HASH_BYTES * DISK_BATCH_SIZE];
          int buffered = 0;
          long lastOffset = 0;
          for (int i = start; i < end; ++i) {
            EthHash.calcDatasetItem(itemBuffer, cache, i);
            System.arraycopy(
                itemBuffer, 0, writeBuffer, buffered * EthHash.HASH_BYTES, EthHash.HASH_BYTES);
            ++buffered;
            if (buffered == DISK_BATCH_SIZE) {
              synchronized (cacheFile) {
                lastOffset =
                    (long) ((i - DISK_BATCH_SIZE + 1) * EthHash.HASH_BYTES) + writeBuffer.length;
                cacheFile.seek(lastOffset - writeBuffer.length);
                cacheFile.write(writeBuffer);
              }
              buffered = 0;
            }
          }
          if (buffered > 0) {
            synchronized (cacheFile) {
              cacheFile.seek(lastOffset);
              cacheFile.write(writeBuffer, 0, buffered * EthHash.HASH_BYTES);
            }
          }
          doneLatch.countDown();
        } catch (final IOException ex) {
          throw new IllegalStateException(ex);
        }
      }
    }
  }
}
