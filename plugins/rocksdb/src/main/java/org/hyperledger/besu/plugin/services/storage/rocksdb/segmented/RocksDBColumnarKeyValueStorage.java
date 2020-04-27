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
package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import static java.util.Objects.requireNonNullElse;

import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbKeyIterator;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbUtil;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageTransactionTransitionValidatorDecorator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
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
import org.rocksdb.Status;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

public class RocksDBColumnarKeyValueStorage
    implements SegmentedKeyValueStorage<ColumnFamilyHandle> {

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  private static final Logger LOG = LogManager.getLogger();
  private static final String DEFAULT_COLUMN = "default";

  private final DBOptions options;
  private final TransactionDBOptions txOptions;
  private final TransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Map<String, ColumnFamilyHandle> columnHandlesByName;
  private final RocksDBMetrics metrics;
  private Optional<byte[]> maybeDoomedKey = Optional.empty();

  public RocksDBColumnarKeyValueStorage(
      final RocksDBConfiguration configuration,
      final List<SegmentIdentifier> segments,
      final MetricsSystem metricsSystem,
      final RocksDBMetricsFactory rocksDBMetricsFactory)
      throws StorageException {

    try {
      final List<ColumnFamilyDescriptor> columnDescriptors =
          segments.stream()
              .map(segment -> new ColumnFamilyDescriptor(segment.getId()))
              .collect(Collectors.toList());
      columnDescriptors.add(
          new ColumnFamilyDescriptor(
              DEFAULT_COLUMN.getBytes(StandardCharsets.UTF_8),
              new ColumnFamilyOptions()
                  .setTableFormatConfig(createBlockBasedTableConfig(configuration))));

      final Statistics stats = new Statistics();
      options =
          new DBOptions()
              .setCreateIfMissing(true)
              .setMaxOpenFiles(configuration.getMaxOpenFiles())
              .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
              .setStatistics(stats)
              .setCreateMissingColumnFamilies(true)
              .setEnv(
                  Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()));

      txOptions = new TransactionDBOptions();
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      db =
          TransactionDB.open(
              options,
              txOptions,
              configuration.getDatabaseDir().toString(),
              columnDescriptors,
              columnHandles);
      metrics = rocksDBMetricsFactory.create(metricsSystem, configuration, db, stats);
      final Map<Bytes, String> segmentsById =
          segments.stream()
              .collect(
                  Collectors.toMap(
                      segment -> Bytes.wrap(segment.getId()), SegmentIdentifier::getName));

      final ImmutableMap.Builder<String, ColumnFamilyHandle> builder = ImmutableMap.builder();

      for (ColumnFamilyHandle columnHandle : columnHandles) {
        final String segmentName =
            requireNonNullElse(
                segmentsById.get(Bytes.wrap(columnHandle.getName())), DEFAULT_COLUMN);
        builder.put(segmentName, columnHandle);
      }
      columnHandlesByName = builder.build();
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  private BlockBasedTableConfig createBlockBasedTableConfig(final RocksDBConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig().setBlockCache(cache);
  }

  @Override
  public ColumnFamilyHandle getSegmentIdentifierByName(final SegmentIdentifier segment) {
    return columnHandlesByName.get(segment.getName());
  }

  @Override
  public Optional<byte[]> get(final ColumnFamilyHandle segment, final byte[] key)
      throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = metrics.getReadLatency().startTimer()) {
      return Optional.ofNullable(db.get(segment, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction<ColumnFamilyHandle> startTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions options = new WriteOptions();
    return new SegmentedKeyValueStorageTransactionTransitionValidatorDecorator<>(
        new RocksDbTransaction(db.beginTransaction(options), options));
  }

  @Override
  public Stream<byte[]> streamKeys(final ColumnFamilyHandle segmentHandle) {
    final RocksIterator rocksIterator = db.newIterator(segmentHandle);
    rocksIterator.seekToFirst();
    return RocksDbKeyIterator.create(rocksIterator).toStream();
  }

  @Override
  public void delete(final ColumnFamilyHandle segmentHandle, final byte[] key) {
    try {
      db.delete(segmentHandle, key);
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public long removeAllKeysUnless(
      final ColumnFamilyHandle segmentHandle, final Predicate<byte[]> inUseCheck) {
    long removedNodeCounter = 0;
    try (final RocksIterator rocksIterator = db.newIterator(segmentHandle)) {
      for (rocksIterator.seekToFirst(); rocksIterator.isValid(); rocksIterator.next()) {
        final byte[] key = rocksIterator.key();
        maybeDoomedKey = Optional.of(key);
        if (!inUseCheck.test(key)) {
          removedNodeCounter++;
          try {
            db.delete(segmentHandle, key);
          } catch (RocksDBException rdbe) {
            // We can't get a lock here because we detect that we are about to commit a doomed key
            // in `commit` below and we are also waiting there. The timeout is configured to be as
            // short as possible so this thread can skip this key and move on to sweeping more keys
            // asap.
            if (rdbe.getStatus().getCode() != Status.Code.TimedOut) throw rdbe;
          }
        }
      }
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
    maybeDoomedKey = Optional.empty();
    return removedNodeCounter;
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final ColumnFamilyHandle segmentHandle, final Predicate<byte[]> returnCondition) {
    final Set<byte[]> returnedKeys = Sets.newIdentityHashSet();
    try (final RocksIterator rocksIterator = db.newIterator(segmentHandle)) {
      for (rocksIterator.seekToFirst(); rocksIterator.isValid(); rocksIterator.next()) {
        final byte[] key = rocksIterator.key();
        if (returnCondition.test(key)) {
          returnedKeys.add(key);
        }
      }
    }
    return returnedKeys;
  }

  @Override
  public void clear(final ColumnFamilyHandle segmentHandle) {
    try (final RocksIterator rocksIterator = db.newIterator(segmentHandle)) {
      rocksIterator.seekToFirst();
      if (rocksIterator.isValid()) {
        final byte[] firstKey = rocksIterator.key();
        rocksIterator.seekToLast();
        if (rocksIterator.isValid()) {
          final byte[] lastKey = rocksIterator.key();
          db.deleteRange(segmentHandle, firstKey, lastKey);
          db.delete(segmentHandle, lastKey);
        }
      }
    } catch (final RocksDBException e) {
      throw new StorageException(e);
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

  private class RocksDbTransaction implements Transaction<ColumnFamilyHandle> {

    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;
    private final Set<Bytes> addedKeys = new HashSet<>();

    RocksDbTransaction(final org.rocksdb.Transaction innerTx, final WriteOptions options) {
      this.innerTx = innerTx;
      this.options = options;
    }

    @Override
    public void put(final ColumnFamilyHandle segment, final byte[] key, final byte[] value) {
      addedKeys.add(Bytes.wrap(key));
      try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
        innerTx.put(segment, key, value);
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public void remove(final ColumnFamilyHandle segment, final byte[] key) {
      try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
        innerTx.delete(segment, key);
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public void commit() throws StorageException {
      // This is where we're intentionally causing the deadlock with the delete in
      // `removeAllKeysUnless` above.
      while (maybeDoomedKey.map(key -> addedKeys.contains(Bytes.wrap(key))).orElse(false)) {
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          throw new StorageException(e);
        }
      }
      try (final OperationTimer.TimingContext ignored = metrics.getCommitLatency().startTimer()) {
        innerTx.commit();
      } catch (final RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    @Override
    public void rollback() {
      try {
        addedKeys.clear();
        innerTx.rollback();
        metrics.getRollbackCount().inc();
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
