/*
 * Copyright Hyperledger Besu Contributors..
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbIterator;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbUtil;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.Status;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The RocksDb columnar key value storage. */
public abstract class RocksDBColumnarKeyValueStorage implements SegmentedKeyValueStorage {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBColumnarKeyValueStorage.class);
  static final String DEFAULT_COLUMN = "default";
  private static final int ROCKSDB_FORMAT_VERSION = 5;
  private static final long ROCKSDB_BLOCK_SIZE = 32768;
  /** RocksDb blockcache size when using the high spec option */
  protected static final long ROCKSDB_BLOCKCACHE_SIZE_HIGH_SPEC = 1_073_741_824L;
  /** RocksDb memtable size when using the high spec option */
  protected static final long ROCKSDB_MEMTABLE_SIZE_HIGH_SPEC = 1_073_741_824L;
  /** RocksDb number of log files to keep on disk */
  private static final long NUMBER_OF_LOG_FILES_TO_KEEP = 7;
  /** RocksDb Time to roll a log file (1 day = 3600 * 24 seconds) */
  private static final long TIME_TO_ROLL_LOG_FILE = 86_400L;

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  /** atomic boolean to track if the storage is closed */
  protected final AtomicBoolean closed = new AtomicBoolean(false);

  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);
  private final ReadOptions readOptions = new ReadOptions().setVerifyChecksums(false);
  private final MetricsSystem metricsSystem;
  private final RocksDBMetricsFactory rocksDBMetricsFactory;
  private final RocksDBConfiguration configuration;
  /** RocksDB DB options */
  protected DBOptions options;

  /** RocksDb transactionDB options */
  protected TransactionDBOptions txOptions;
  /** RocksDb statistics */
  protected final Statistics stats = new Statistics();

  /** RocksDB metrics */
  protected RocksDBMetrics metrics;

  /** Map of the columns handles by name */
  protected Map<SegmentIdentifier, RocksDbSegmentIdentifier> columnHandlesBySegmentIdentifier;
  /** Column descriptors */
  protected List<ColumnFamilyDescriptor> columnDescriptors;
  /** Column handles */
  protected List<ColumnFamilyHandle> columnHandles;

  /** Trimmed segments */
  protected List<SegmentIdentifier> trimmedSegments;

  /**
   * Instantiates a new Rocks db columnar key value storage.
   *
   * @param configuration the configuration
   * @param defaultSegments the segments
   * @param ignorableSegments the ignorable segments
   * @param metricsSystem the metrics system
   * @param rocksDBMetricsFactory the rocks db metrics factory
   * @throws StorageException the storage exception
   */
  public RocksDBColumnarKeyValueStorage(
      final RocksDBConfiguration configuration,
      final List<SegmentIdentifier> defaultSegments,
      final List<SegmentIdentifier> ignorableSegments,
      final MetricsSystem metricsSystem,
      final RocksDBMetricsFactory rocksDBMetricsFactory)
      throws StorageException {

    this.configuration = configuration;
    this.metricsSystem = metricsSystem;
    this.rocksDBMetricsFactory = rocksDBMetricsFactory;

    try {
      final ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
      trimmedSegments = new ArrayList<>(defaultSegments);
      final List<byte[]> existingColumnFamilies =
          RocksDB.listColumnFamilies(new Options(), configuration.getDatabaseDir().toString());
      // Only ignore if not existed currently
      ignorableSegments.stream()
          .filter(
              ignorableSegment ->
                  existingColumnFamilies.stream()
                      .noneMatch(existed -> Arrays.equals(existed, ignorableSegment.getId())))
          .forEach(trimmedSegments::remove);
      columnDescriptors =
          trimmedSegments.stream().map(this::createColumnDescriptor).collect(Collectors.toList());
      columnDescriptors.add(
          new ColumnFamilyDescriptor(
              DEFAULT_COLUMN.getBytes(StandardCharsets.UTF_8),
              columnFamilyOptions
                  .setTtl(0)
                  .setCompressionType(CompressionType.LZ4_COMPRESSION)
                  .setTableFormatConfig(createBlockBasedTableConfig(configuration))));

      setGlobalOptions(configuration, stats);

      txOptions =
          new TransactionDBOptions()
              .setDefaultLockTimeout(configuration.getDefaultTimeout())
              .setTransactionLockTimeout(configuration.getTransactionLockTimeout());
      columnHandles = new ArrayList<>(columnDescriptors.size());
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  private ColumnFamilyDescriptor createColumnDescriptor(final SegmentIdentifier segment) {
    final var options =
        new ColumnFamilyOptions()
            .setTtl(0)
            .setCompressionType(CompressionType.LZ4_COMPRESSION)
            .setTableFormatConfig(createBlockBasedTableConfig(configuration));

    if (segment.containsStaticData()) {
      options
          .setEnableBlobFiles(true)
          .setEnableBlobGarbageCollection(false)
          .setMinBlobSize(100)
          .setBlobCompressionType(CompressionType.LZ4_COMPRESSION);
    }

    return new ColumnFamilyDescriptor(segment.getId(), options);
  }

  private void setGlobalOptions(final RocksDBConfiguration configuration, final Statistics stats) {
    options = new DBOptions();
    options
        .setCreateIfMissing(true)
        .setMaxOpenFiles(configuration.getMaxOpenFiles())
        .setStatistics(stats)
        .setCreateMissingColumnFamilies(true)
        .setLogFileTimeToRoll(TIME_TO_ROLL_LOG_FILE)
        .setKeepLogFileNum(NUMBER_OF_LOG_FILES_TO_KEEP)
        .setEnv(Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()));

    if (configuration.isHighSpec()) {
      options.setDbWriteBufferSize(ROCKSDB_MEMTABLE_SIZE_HIGH_SPEC);
    }
  }

  void initMetrics() {
    metrics = rocksDBMetricsFactory.create(metricsSystem, configuration, getDB(), stats);
  }

  void initColumnHandles() throws RocksDBException {
    // will not include the DEFAULT columnHandle, we do not use it:
    columnHandlesBySegmentIdentifier =
        trimmedSegments.stream()
            .collect(
                Collectors.toMap(
                    segmentId -> segmentId,
                    segment -> {
                      var columnHandle =
                          columnHandles.stream()
                              .filter(
                                  ch -> {
                                    try {
                                      return Arrays.equals(ch.getName(), segment.getId());
                                    } catch (RocksDBException e) {
                                      throw new RuntimeException(e);
                                    }
                                  })
                              .findFirst()
                              .orElseThrow(
                                  () ->
                                      new RuntimeException(
                                          "Column handle not found for segment "
                                              + segment.getName()));
                      return new RocksDbSegmentIdentifier(getDB(), columnHandle);
                    }));
  }

  BlockBasedTableConfig createBlockBasedTableConfig(final RocksDBConfiguration config) {
    final LRUCache cache =
        new LRUCache(
            config.isHighSpec() ? ROCKSDB_BLOCKCACHE_SIZE_HIGH_SPEC : config.getCacheCapacity());
    return new BlockBasedTableConfig()
        .setFormatVersion(ROCKSDB_FORMAT_VERSION)
        .setBlockCache(cache)
        .setFilterPolicy(new BloomFilter(10, false))
        .setPartitionFilters(true)
        .setCacheIndexAndFilterBlocks(false)
        .setBlockSize(ROCKSDB_BLOCK_SIZE);
  }

  /**
   * Safe method to map segment identifier to column handle.
   *
   * @param segment segment identifier
   * @return column handle
   */
  protected ColumnFamilyHandle safeColumnHandle(final SegmentIdentifier segment) {
    RocksDbSegmentIdentifier safeRef = columnHandlesBySegmentIdentifier.get(segment);
    if (safeRef == null) {
      throw new RuntimeException("Column handle not found for segment " + segment.getName());
    }
    return safeRef.get();
  }

  @Override
  public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();

    try (final OperationTimer.TimingContext ignored = metrics.getReadLatency().startTimer()) {
      return Optional.ofNullable(getDB().get(safeColumnHandle(segment), readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segmentIdentifier) {
    final RocksIterator rocksIterator = getDB().newIterator(safeColumnHandle(segmentIdentifier));
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segmentIdentifier, final byte[] startKey) {
    final RocksIterator rocksIterator = getDB().newIterator(safeColumnHandle(segmentIdentifier));
    rocksIterator.seek(startKey);
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  @Override
  public Stream<byte[]> streamKeys(final SegmentIdentifier segmentIdentifier) {
    final RocksIterator rocksIterator = getDB().newIterator(safeColumnHandle(segmentIdentifier));
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStreamKeys();
  }

  @Override
  public boolean tryDelete(final SegmentIdentifier segmentIdentifier, final byte[] key) {
    try {
      getDB().delete(safeColumnHandle(segmentIdentifier), tryDeleteOptions, key);
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
      final SegmentIdentifier segmentIdentifier, final Predicate<byte[]> returnCondition) {
    return stream(segmentIdentifier)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getKey)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(
      final SegmentIdentifier segmentIdentifier, final Predicate<byte[]> returnCondition) {
    return stream(segmentIdentifier)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public void clear(final SegmentIdentifier segmentIdentifier) {
    Optional.ofNullable(columnHandlesBySegmentIdentifier.get(segmentIdentifier))
        .ifPresent(RocksDbSegmentIdentifier::reset);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      txOptions.close();
      options.close();
      tryDeleteOptions.close();
      columnHandlesBySegmentIdentifier.values().stream()
          .map(RocksDbSegmentIdentifier::get)
          .forEach(ColumnFamilyHandle::close);
      getDB().close();
    }
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  abstract RocksDB getDB();
}
