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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.primitives.Longs;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class RocksDbTaskQueue implements BytesTaskQueue {

  private final Options options;
  private final RocksDB db;

  private final AtomicLong lastEnqueuedKey = new AtomicLong(0);
  private final AtomicLong lastDequeuedKey = new AtomicLong(0);
  private final AtomicLong oldestKey = new AtomicLong(0);
  private final Set<RocksDbTask> outstandingTasks = new HashSet<>();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final OperationTimer enqueueLatency;
  private final OperationTimer dequeueLatency;

  private RocksDbTaskQueue(final Path storageDirectory, final MetricsSystem metricsSystem) {
    try {
      RocksDbUtil.loadNativeLibrary();
      options = new Options().setCreateIfMissing(true);
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

  public static RocksDbTaskQueue create(
      final Path storageDirectory, final MetricsSystem metricsSystem) {
    return new RocksDbTaskQueue(storageDirectory, metricsSystem);
  }

  @Override
  public synchronized void enqueue(final BytesValue taskData) {
    assertNotClosed();
    try (final OperationTimer.TimingContext ignored = enqueueLatency.startTimer()) {
      byte[] key = Longs.toByteArray(lastEnqueuedKey.incrementAndGet());
      db.put(key, taskData.getArrayUnsafe());
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized Task<BytesValue> dequeue() {
    assertNotClosed();
    if (isEmpty()) {
      return null;
    }
    try (final OperationTimer.TimingContext ignored = dequeueLatency.startTimer()) {
      long key = lastDequeuedKey.incrementAndGet();
      byte[] value = db.get(Longs.toByteArray(key));
      if (value == null) {
        throw new IllegalStateException("Next expected value is missing");
      }

      BytesValue data = BytesValue.of(value);
      RocksDbTask task = new RocksDbTask(this, data, key);
      outstandingTasks.add(task);
      return task;
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
  public synchronized boolean isEmpty() {
    assertNotClosed();
    return size() == 0;
  }

  @Override
  public synchronized void clear() {
    assertNotClosed();
    outstandingTasks.clear();
    byte[] from = Longs.toByteArray(oldestKey.get());
    byte[] to = Longs.toByteArray(lastEnqueuedKey.get() + 1);
    try {
      db.deleteRange(from, to);
      lastDequeuedKey.set(0);
      lastEnqueuedKey.set(0);
      oldestKey.set(0);
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized boolean allTasksCompleted() {
    return isEmpty() && outstandingTasks.isEmpty();
  }

  private synchronized void deleteCompletedTasks() {
    long oldestOutstandingKey =
        outstandingTasks.stream()
            .min(Comparator.comparingLong(RocksDbTask::getKey))
            .map(RocksDbTask::getKey)
            .orElse(lastDequeuedKey.get() + 1);

    if (oldestKey.get() < oldestOutstandingKey) {
      // Delete all contiguous completed tasks
      byte[] fromKey = Longs.toByteArray(oldestKey.get());
      byte[] toKey = Longs.toByteArray(oldestOutstandingKey);
      try {
        db.deleteRange(fromKey, toKey);
        oldestKey.set(oldestOutstandingKey);
      } catch (RocksDBException e) {
        throw new StorageException(e);
      }
    }
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
      throw new IllegalStateException("Attempt to access closed " + getClass().getSimpleName());
    }
  }

  private synchronized boolean markTaskCompleted(final RocksDbTask task) {
    if (outstandingTasks.remove(task)) {
      deleteCompletedTasks();
      return true;
    }
    return false;
  }

  private synchronized void handleFailedTask(final RocksDbTask task) {
    if (markTaskCompleted(task)) {
      enqueue(task.getData());
    }
  }

  public static class StorageException extends RuntimeException {
    StorageException(final Throwable t) {
      super(t);
    }
  }

  private static class RocksDbTask implements Task<BytesValue> {
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final RocksDbTaskQueue parentQueue;
    private final BytesValue data;
    private final long key;

    private RocksDbTask(final RocksDbTaskQueue parentQueue, final BytesValue data, final long key) {
      this.parentQueue = parentQueue;
      this.data = data;
      this.key = key;
    }

    public long getKey() {
      return key;
    }

    @Override
    public BytesValue getData() {
      return data;
    }

    @Override
    public void markCompleted() {
      if (completed.compareAndSet(false, true)) {
        parentQueue.markTaskCompleted(this);
      }
    }

    @Override
    public void markFailed() {
      if (completed.compareAndSet(false, true)) {
        parentQueue.handleFailedTask(this);
      }
    }
  }
}
