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
import java.util.function.Function;

import com.google.common.primitives.Longs;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

public class RocksDbTaskQueue<T> implements TaskQueue<T> {

  private final Options options;
  private final RocksDB db;

  private final AtomicLong lastEnqueuedKey = new AtomicLong(0);
  private final AtomicLong lastDequeuedKey = new AtomicLong(0);
  private RocksIterator dequeueIterator;
  private final AtomicLong oldestKey = new AtomicLong(0);
  private final Set<RocksDbTask<T>> outstandingTasks = new HashSet<>();

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final Function<T, BytesValue> serializer;
  private final Function<BytesValue, T> deserializer;

  private final OperationTimer enqueueLatency;
  private final OperationTimer dequeueLatency;

  private RocksDbTaskQueue(
      final Path storageDirectory,
      final Function<T, BytesValue> serializer,
      final Function<BytesValue, T> deserializer,
      final MetricsSystem metricsSystem) {
    this.serializer = serializer;
    this.deserializer = deserializer;
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

  public static <T> RocksDbTaskQueue<T> create(
      final Path storageDirectory,
      final Function<T, BytesValue> serializer,
      final Function<BytesValue, T> deserializer,
      final MetricsSystem metricsSystem) {
    return new RocksDbTaskQueue<>(storageDirectory, serializer, deserializer, metricsSystem);
  }

  @Override
  public synchronized void enqueue(final T taskData) {
    assertNotClosed();
    try (final OperationTimer.TimingContext ignored = enqueueLatency.startTimer()) {
      final byte[] key = Longs.toByteArray(lastEnqueuedKey.incrementAndGet());
      db.put(key, serializer.apply(taskData).getArrayUnsafe());
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized Task<T> dequeue() {
    assertNotClosed();
    if (isEmpty()) {
      return null;
    }
    try (final OperationTimer.TimingContext ignored = dequeueLatency.startTimer()) {
      if (dequeueIterator == null) {
        dequeueIterator = db.newIterator();
      }
      final long key = lastDequeuedKey.incrementAndGet();
      dequeueIterator.seek(Longs.toByteArray(key));
      if (!dequeueIterator.isValid()) {
        // Reached the end of the snapshot this iterator was loaded with
        dequeueIterator.close();
        dequeueIterator = db.newIterator();
        dequeueIterator.seek(Longs.toByteArray(key));
        if (!dequeueIterator.isValid()) {
          throw new IllegalStateException("Next expected value is missing");
        }
      }
      final byte[] value = dequeueIterator.value();
      final BytesValue data = BytesValue.of(value);
      final RocksDbTask<T> task = new RocksDbTask<>(this, deserializer.apply(data), key);
      outstandingTasks.add(task);
      return task;
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
    final byte[] from = Longs.toByteArray(oldestKey.get());
    final byte[] to = Longs.toByteArray(lastEnqueuedKey.get() + 1);
    try {
      db.deleteRange(from, to);
      if (dequeueIterator != null) {
        dequeueIterator.close();
        dequeueIterator = null;
      }
      lastDequeuedKey.set(0);
      lastEnqueuedKey.set(0);
      oldestKey.set(0);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized boolean allTasksCompleted() {
    return isEmpty() && outstandingTasks.isEmpty();
  }

  private synchronized void deleteCompletedTasks() {
    final long oldestOutstandingKey =
        outstandingTasks.stream()
            .min(Comparator.comparingLong(RocksDbTask::getKey))
            .map(RocksDbTask::getKey)
            .orElse(lastDequeuedKey.get() + 1);

    if (oldestKey.get() < oldestOutstandingKey) {
      // Delete all contiguous completed tasks
      final byte[] fromKey = Longs.toByteArray(oldestKey.get());
      final byte[] toKey = Longs.toByteArray(oldestOutstandingKey);
      try {
        db.deleteRange(fromKey, toKey);
        oldestKey.set(oldestOutstandingKey);
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      if (dequeueIterator != null) {
        dequeueIterator.close();
      }
      options.close();
      db.close();
    }
  }

  private void assertNotClosed() {
    if (closed.get()) {
      throw new IllegalStateException("Attempt to access closed " + getClass().getSimpleName());
    }
  }

  private synchronized boolean markTaskCompleted(final RocksDbTask<T> task) {
    if (outstandingTasks.remove(task)) {
      deleteCompletedTasks();
      return true;
    }
    return false;
  }

  private synchronized void handleFailedTask(final RocksDbTask<T> task) {
    if (markTaskCompleted(task)) {
      enqueue(task.getData());
    }
  }

  public static class StorageException extends RuntimeException {
    StorageException(final Throwable t) {
      super(t);
    }
  }

  private static class RocksDbTask<T> implements Task<T> {
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final RocksDbTaskQueue<T> parentQueue;
    private final T data;
    private final long key;

    private RocksDbTask(final RocksDbTaskQueue<T> parentQueue, final T data, final long key) {
      this.parentQueue = parentQueue;
      this.data = data;
      this.key = key;
    }

    public long getKey() {
      return key;
    }

    @Override
    public T getData() {
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
