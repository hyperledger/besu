/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.services.queue;

import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.services.util.RocksDbUtil;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.primitives.Longs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class RocksDbQueue implements BytesQueue {

  private static final Logger LOG = LogManager.getLogger();

  private final Options options;
  private final RocksDB db;

  private final AtomicLong lastEnqueuedKey = new AtomicLong(0);
  private final AtomicLong lastDequeuedKey = new AtomicLong(0);
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final OperationTimer enqueueLatency;
  private final OperationTimer dequeueLatency;

  private RocksDbQueue(final Path storageDirectory, final MetricsSystem metricsSystem) {
    try {
      RocksDbUtil.loadNativeLibrary();
      options =
          new Options()
              .setCreateIfMissing(true)
              // TODO: Support restoration from a previously persisted queue
              .setErrorIfExists(true);
      db = RocksDB.open(options, storageDirectory.toString());

      enqueueLatency =
          metricsSystem.createTimer(
              MetricCategory.BIG_QUEUE,
              "enqueue_latency_seconds",
              "Latency for enqueuing an item.");
      dequeueLatency =
          metricsSystem.createTimer(
              MetricCategory.BIG_QUEUE,
              "dequeue_latency_seconds",
              "Latency for dequeuing an item.");
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  public static RocksDbQueue create(
      final Path storageDirectory, final MetricsSystem metricsSystem) {
    return new RocksDbQueue(storageDirectory, metricsSystem);
  }

  @Override
  public synchronized void enqueue(final BytesValue value) {
    assertNotClosed();
    try (final OperationTimer.TimingContext ignored = enqueueLatency.startTimer()) {
      byte[] key = Longs.toByteArray(lastEnqueuedKey.incrementAndGet());
      db.put(key, value.getArrayUnsafe());
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized BytesValue dequeue() {
    assertNotClosed();
    if (size() == 0) {
      return null;
    }
    try (final OperationTimer.TimingContext ignored = dequeueLatency.startTimer()) {
      byte[] key = Longs.toByteArray(lastDequeuedKey.incrementAndGet());
      byte[] value = db.get(key);
      if (value == null) {
        throw new IllegalStateException("Next expected value is missing");
      }
      db.delete(key);

      return BytesValue.of(value);
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized long size() {
    assertNotClosed();
    return lastEnqueuedKey.get() - lastDequeuedKey.get();
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      options.close();
      db.close();
    }
  }

  private void assertNotClosed() {
    if (closed.get()) {
      throw new IllegalStateException(
          "Attempt to access closed " + RocksDbQueue.class.getSimpleName());
    }
  }

  public static class StorageException extends RuntimeException {
    StorageException(final Throwable t) {
      super(t);
    }
  }
}
