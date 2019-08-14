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

import static java.util.Objects.requireNonNullElse;

import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.services.util.RocksDbUtil;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

public class ColumnarRocksDbKeyValueStorage
    implements SegmentedKeyValueStorage<ColumnFamilyHandle>, Closeable {

  private static final Logger LOG = LogManager.getLogger();
  private static final String DEFAULT_COLUMN = "default";

  private final DBOptions options;
  private final TransactionDBOptions txOptions;
  private final TransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Map<String, ColumnFamilyHandle> columnHandlesByName;
  private final RocksDBMetricsHelper rocksDBMetricsHelper;

  public static ColumnarRocksDbKeyValueStorage create(
      final RocksDbConfiguration rocksDbConfiguration,
      final List<Segment> segments,
      final MetricsSystem metricsSystem)
      throws StorageException {
    return new ColumnarRocksDbKeyValueStorage(rocksDbConfiguration, segments, metricsSystem);
  }

  private ColumnarRocksDbKeyValueStorage(
      final RocksDbConfiguration rocksDbConfiguration,
      final List<Segment> segments,
      final MetricsSystem metricsSystem) {
    RocksDbUtil.loadNativeLibrary();
    try {
      final List<ColumnFamilyDescriptor> columnDescriptors =
          segments.stream()
              .map(segment -> new ColumnFamilyDescriptor(segment.getId()))
              .collect(Collectors.toList());
      columnDescriptors.add(
          new ColumnFamilyDescriptor(
              DEFAULT_COLUMN.getBytes(StandardCharsets.UTF_8),
              new ColumnFamilyOptions()
                  .setTableFormatConfig(createBlockBasedTableConfig(rocksDbConfiguration))));

      final Statistics stats = new Statistics();
      options =
          new DBOptions()
              .setCreateIfMissing(true)
              .setMaxOpenFiles(rocksDbConfiguration.getMaxOpenFiles())
              .setMaxBackgroundCompactions(rocksDbConfiguration.getMaxBackgroundCompactions())
              .setStatistics(stats)
              .setCreateMissingColumnFamilies(true)
              .setEnv(
                  Env.getDefault()
                      .setBackgroundThreads(rocksDbConfiguration.getBackgroundThreadCount()));

      txOptions = new TransactionDBOptions();
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      db =
          TransactionDB.open(
              options,
              txOptions,
              rocksDbConfiguration.getDatabaseDir().toString(),
              columnDescriptors,
              columnHandles);
      rocksDBMetricsHelper =
          RocksDBMetricsHelper.of(metricsSystem, rocksDbConfiguration, db, stats);
      final Map<BytesValue, String> segmentsById =
          segments.stream()
              .collect(
                  Collectors.toMap(segment -> BytesValue.wrap(segment.getId()), Segment::getName));

      final ImmutableMap.Builder<String, ColumnFamilyHandle> builder = ImmutableMap.builder();

      for (ColumnFamilyHandle columnHandle : columnHandles) {
        final String segmentName =
            requireNonNullElse(
                segmentsById.get(BytesValue.wrap(columnHandle.getName())), DEFAULT_COLUMN);
        builder.put(segmentName, columnHandle);
      }
      columnHandlesByName = builder.build();

    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  private BlockBasedTableConfig createBlockBasedTableConfig(final RocksDbConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig().setBlockCache(cache);
  }

  @Override
  public ColumnFamilyHandle getSegmentIdentifierByName(final Segment segment) {
    return columnHandlesByName.get(segment.getName());
  }

  @Override
  public Optional<BytesValue> get(final ColumnFamilyHandle segment, final BytesValue key)
      throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored =
        rocksDBMetricsHelper.getReadLatency().startTimer()) {
      return Optional.ofNullable(db.get(segment, key.getArrayUnsafe())).map(BytesValue::wrap);
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction<ColumnFamilyHandle> startTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    return new RocksDbTransaction(db.beginTransaction(options), options);
  }

  @Override
  public long removeUnless(
      final ColumnFamilyHandle segmentHandle, final Predicate<BytesValue> inUseCheck) {
    long removedNodeCounter = 0;
    try (final RocksIterator rocksIterator = db.newIterator(segmentHandle)) {
      rocksIterator.seekToFirst();
      while (rocksIterator.isValid()) {
        final byte[] key = rocksIterator.key();
        if (!inUseCheck.test(BytesValue.wrap(key))) {
          removedNodeCounter++;
          db.delete(segmentHandle, key);
        }
        rocksIterator.next();
      }
    } catch (final RocksDBException e) {
      throw new KeyValueStorage.StorageException(e);
    }
    return removedNodeCounter;
  }

  @Override
  public void clear(final ColumnFamilyHandle segmentHandle) {
    try (final RocksIterator rocksIterator = db.newIterator(segmentHandle)) {
      rocksIterator.seekToFirst();
      if (rocksIterator.isValid()) {
        final byte[] firstKey = rocksIterator.key();
        rocksIterator.seekToLast();
        if (rocksIterator.isValid()) {
          db.deleteRange(segmentHandle, firstKey, rocksIterator.key());
        }
      }
    } catch (final RocksDBException e) {
      throw new KeyValueStorage.StorageException(e);
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      columnHandlesByName.values().forEach(ColumnFamilyHandle::close);
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  private class RocksDbTransaction extends AbstractTransaction<ColumnFamilyHandle> {
    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;

    RocksDbTransaction(final org.rocksdb.Transaction innerTx, final WriteOptions options) {
      this.innerTx = innerTx;
      this.options = options;
    }

    @Override
    protected void doPut(
        final ColumnFamilyHandle segment, final BytesValue key, final BytesValue value) {
      try (final OperationTimer.TimingContext ignored =
          rocksDBMetricsHelper.getWriteLatency().startTimer()) {
        innerTx.put(segment, key.getArrayUnsafe(), value.getArrayUnsafe());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doRemove(final ColumnFamilyHandle segment, final BytesValue key) {
      try (final OperationTimer.TimingContext ignored =
          rocksDBMetricsHelper.getRemoveLatency().startTimer()) {
        innerTx.delete(segment, key.getArrayUnsafe());
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    protected void doCommit() throws StorageException {
      try (final OperationTimer.TimingContext ignored =
          rocksDBMetricsHelper.getCommitLatency().startTimer()) {
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
        rocksDBMetricsHelper.getRollbackCount().inc();
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
