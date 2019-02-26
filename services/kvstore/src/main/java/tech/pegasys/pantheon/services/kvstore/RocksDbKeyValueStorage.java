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
package tech.pegasys.pantheon.services.kvstore;

import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.services.util.RocksDbUtil;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

public class RocksDbKeyValueStorage implements KeyValueStorage, Closeable {

  private static final Logger LOG = LogManager.getLogger();

  private final Options options;
  private final TransactionDBOptions txOptions;
  private final TransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final OperationTimer readLatency;
  private final OperationTimer removeLatency;
  private final OperationTimer writeLatency;
  private final OperationTimer commitLatency;
  private final Counter rollbackCount;

  public static KeyValueStorage create(
      final Path storageDirectory, final MetricsSystem metricsSystem) throws StorageException {
    return new RocksDbKeyValueStorage(storageDirectory, metricsSystem);
  }

  private RocksDbKeyValueStorage(final Path storageDirectory, final MetricsSystem metricsSystem) {
    RocksDbUtil.loadNativeLibrary();
    try {
      options = new Options().setCreateIfMissing(true);
      txOptions = new TransactionDBOptions();
      db = TransactionDB.open(options, txOptions, storageDirectory.toString());

      readLatency =
          metricsSystem.createTimer(
              MetricCategory.ROCKSDB, "read_latency_seconds", "Latency for read from RocksDB.");
      removeLatency =
          metricsSystem.createTimer(
              MetricCategory.ROCKSDB,
              "remove_latency_seconds",
              "Latency of remove requests from RocksDB.");
      writeLatency =
          metricsSystem.createTimer(
              MetricCategory.ROCKSDB, "write_latency_seconds", "Latency for write to RocksDB.");
      commitLatency =
          metricsSystem.createTimer(
              MetricCategory.ROCKSDB, "commit_latency_seconds", "Latency for commits to RocksDB.");

      rollbackCount =
          metricsSystem.createCounter(
              MetricCategory.ROCKSDB,
              "rollback_count",
              "Number of RocksDB transactions rolled back.");
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<BytesValue> get(final BytesValue key) throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = readLatency.startTimer()) {
      return Optional.ofNullable(db.get(key.extractArray())).map(BytesValue::wrap);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction startTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    return new RocksDbTransaction(db.beginTransaction(options), options);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  /**
   * Iterates over rocksDB key-value entries. Reads from a db snapshot implicitly taken when the
   * RocksIterator passed to the constructor was created.
   *
   * <p>Implements {@link AutoCloseable} and can be used with try-with-resources construct. When
   * transformed to a stream (see {@link #toStream}), iterator is automatically closed when the
   * stream is closed.
   */
  private static class RocksDbEntryIterator implements Iterator<Entry>, AutoCloseable {
    private final RocksIterator rocksIt;
    private volatile boolean closed = false;

    RocksDbEntryIterator(final RocksIterator rocksIt) {
      this.rocksIt = rocksIt;
    }

    @Override
    public boolean hasNext() {
      return rocksIt.isValid();
    }

    @Override
    public Entry next() {
      if (closed) {
        throw new IllegalStateException("Attempt to read from a closed RocksDbEntryIterator.");
      }
      try {
        rocksIt.status();
      } catch (final RocksDBException e) {
        LOG.error("RocksDbEntryIterator encountered a problem while iterating.", e);
      }
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final Entry entry =
          Entry.create(BytesValue.wrap(rocksIt.key()), BytesValue.wrap(rocksIt.value()));
      rocksIt.next();
      return entry;
    }

    Stream<Entry> toStream() {
      final Spliterator<Entry> split =
          Spliterators.spliteratorUnknownSize(
              this, Spliterator.IMMUTABLE | Spliterator.DISTINCT | Spliterator.NONNULL);

      return StreamSupport.stream(split, false).onClose(this::close);
    }

    @Override
    public void close() {
      rocksIt.close();
      closed = true;
    }
  }

  private class RocksDbTransaction extends AbstractTransaction {
    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;

    RocksDbTransaction(final org.rocksdb.Transaction innerTx, final WriteOptions options) {
      this.innerTx = innerTx;
      this.options = options;
    }

    @Override
    protected void doPut(final BytesValue key, final BytesValue value) {
      try (final OperationTimer.TimingContext ignored = writeLatency.startTimer()) {
        innerTx.put(key.extractArray(), value.extractArray());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doRemove(final BytesValue key) {
      try (final OperationTimer.TimingContext ignored = removeLatency.startTimer()) {
        innerTx.delete(key.extractArray());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doCommit() throws StorageException {
      try (final OperationTimer.TimingContext ignored = commitLatency.startTimer()) {
        innerTx.commit();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    @Override
    protected void doRollback() {
      try {
        innerTx.rollback();
        rollbackCount.inc();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    private void close() {
      innerTx.close();
      options.close();
    }
  }
}
