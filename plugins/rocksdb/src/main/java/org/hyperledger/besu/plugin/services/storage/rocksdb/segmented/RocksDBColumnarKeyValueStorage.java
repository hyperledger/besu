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
import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbKeyIterator;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbUtil;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageTransactionTransitionValidatorDecorator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDBColumnarKeyValueStorage
    implements SegmentedKeyValueStorage<RocksDbSegmentIdentifier> {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBColumnarKeyValueStorage.class);
  private static final String DEFAULT_COLUMN = "default";
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";
  private static final int ROCKSDB_FORMAT_VERSION = 5;
  private static final long ROCKSDB_BLOCK_SIZE = 32768;

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  private final DBOptions options;
  private final TransactionDBOptions txOptions;
  private final TransactionDB db;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Map<String, RocksDbSegmentIdentifier> columnHandlesByName;
  private final RocksDBMetrics metrics;
  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);

  public RocksDBColumnarKeyValueStorage(
      final RocksDBConfiguration configuration,
      final List<SegmentIdentifier> segments,
      final MetricsSystem metricsSystem,
      final RocksDBMetricsFactory rocksDBMetricsFactory)
      throws StorageException {

    try (final ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()) {
      final List<ColumnFamilyDescriptor> columnDescriptors =
          segments.stream()
              .map(
                  segment ->
                      new ColumnFamilyDescriptor(
                          segment.getId(),
                          new ColumnFamilyOptions()
                              .setTtl(0)
                              .setTableFormatConfig(createBlockBasedTableConfig(configuration))))
              .collect(Collectors.toList());
      columnDescriptors.add(
          new ColumnFamilyDescriptor(
              DEFAULT_COLUMN.getBytes(StandardCharsets.UTF_8),
              columnFamilyOptions
                  .setTtl(0)
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

      final ImmutableMap.Builder<String, RocksDbSegmentIdentifier> builder = ImmutableMap.builder();

      for (ColumnFamilyHandle columnHandle : columnHandles) {
        final String segmentName =
            requireNonNullElse(
                segmentsById.get(Bytes.wrap(columnHandle.getName())), DEFAULT_COLUMN);
        builder.put(segmentName, new RocksDbSegmentIdentifier(db, columnHandle));
      }
      columnHandlesByName = builder.build();

    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  private BlockBasedTableConfig createBlockBasedTableConfig(final RocksDBConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig()
        .setBlockCache(cache)
        .setFormatVersion(ROCKSDB_FORMAT_VERSION)
        .setOptimizeFiltersForMemory(true)
        .setCacheIndexAndFilterBlocks(true)
        .setBlockSize(ROCKSDB_BLOCK_SIZE);
  }

  @Override
  public RocksDbSegmentIdentifier getSegmentIdentifierByName(final SegmentIdentifier segment) {
    return columnHandlesByName.get(segment.getName());
  }

  @Override
  public Optional<byte[]> get(final RocksDbSegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = metrics.getReadLatency().startTimer()) {
      return Optional.ofNullable(db.get(segment.get(), key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Transaction<RocksDbSegmentIdentifier> startTransaction() throws StorageException {
    throwIfClosed();
    final WriteOptions writeOptions = new WriteOptions();
    writeOptions.setIgnoreMissingColumnFamilies(true);
    return new SegmentedKeyValueStorageTransactionTransitionValidatorDecorator<>(
        new RocksDbTransaction(db.beginTransaction(writeOptions), writeOptions));
  }

  @Override
  public Stream<byte[]> streamKeys(final RocksDbSegmentIdentifier segmentHandle) {
    final RocksIterator rocksIterator = db.newIterator(segmentHandle.get());
    rocksIterator.seekToFirst();
    return RocksDbKeyIterator.create(rocksIterator).toStream();
  }

  @Override
  public boolean tryDelete(final RocksDbSegmentIdentifier segmentHandle, final byte[] key) {
    try {
      db.delete(segmentHandle.get(), tryDeleteOptions, key);
      return true;
    } catch (RocksDBException e) {
      if (e.getStatus().getCode() == Status.Code.Incomplete) {
        return false;
      } else {
        throw new StorageException(e);
      }
    }
  }

  @Override
  public Set<byte[]> getAllKeysThat(
      final RocksDbSegmentIdentifier segmentHandle, final Predicate<byte[]> returnCondition) {
    return streamKeys(segmentHandle).filter(returnCondition).collect(toUnmodifiableSet());
  }

  @Override
  public void clear(final RocksDbSegmentIdentifier segmentHandle) {

    columnHandlesByName.values().stream()
        .filter(e -> e.equals(segmentHandle))
        .findAny()
        .ifPresent(segmentIdentifier -> segmentIdentifier.reset());
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      tryDeleteOptions.close();
      columnHandlesByName.values().stream()
          .map(RocksDbSegmentIdentifier::get)
          .forEach(ColumnFamilyHandle::close);
      db.close();
    }
  }

  private void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  private class RocksDbTransaction implements Transaction<RocksDbSegmentIdentifier> {

    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;

    RocksDbTransaction(final org.rocksdb.Transaction innerTx, final WriteOptions options) {
      this.innerTx = innerTx;
      this.options = options;
    }

    @Override
    public void put(final RocksDbSegmentIdentifier segment, final byte[] key, final byte[] value) {
      try (final OperationTimer.TimingContext ignored = metrics.getWriteLatency().startTimer()) {
        innerTx.put(segment.get(), key, value);
      } catch (final RocksDBException e) {
        if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
          LOG.error(e.getMessage());
          System.exit(0);
        }
        throw new StorageException(e);
      }
    }

    @Override
    public void remove(final RocksDbSegmentIdentifier segment, final byte[] key) {
      try (final OperationTimer.TimingContext ignored = metrics.getRemoveLatency().startTimer()) {
        innerTx.delete(segment.get(), key);
      } catch (final RocksDBException e) {
        if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
          LOG.error(e.getMessage());
          System.exit(0);
        }
        throw new StorageException(e);
      }
    }

    @Override
    public void commit() throws StorageException {
      try (final OperationTimer.TimingContext ignored = metrics.getCommitLatency().startTimer()) {
        innerTx.commit();
      } catch (final RocksDBException e) {
        if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
          LOG.error(e.getMessage());
          System.exit(0);
        }
        throw new StorageException(e);
      } finally {
        close();
      }
    }

    @Override
    public void rollback() {
      try {
        innerTx.rollback();
        metrics.getRollbackCount().inc();
      } catch (final RocksDBException e) {
        if (e.getMessage().contains(NO_SPACE_LEFT_ON_DEVICE)) {
          LOG.error(e.getMessage());
          System.exit(0);
        }
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
