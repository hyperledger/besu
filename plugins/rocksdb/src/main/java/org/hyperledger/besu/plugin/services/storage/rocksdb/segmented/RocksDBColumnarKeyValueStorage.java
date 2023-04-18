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

import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toUnmodifiableSet;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbIterator;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbUtil;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;

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

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
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
public abstract class RocksDBColumnarKeyValueStorage
    implements SegmentedKeyValueStorage<RocksDbSegmentIdentifier> {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBColumnarKeyValueStorage.class);
  static final String DEFAULT_COLUMN = "default";
  private static final String NO_SPACE_LEFT_ON_DEVICE = "No space left on device";
  private static final int ROCKSDB_FORMAT_VERSION = 5;
  private static final long ROCKSDB_BLOCK_SIZE = 32768;
  /** RocksDb blockcache size when using the high spec option */
  protected static final long ROCKSDB_BLOCKCACHE_SIZE_HIGH_SPEC = 1_073_741_824L;
  /** RocksDb memtable size when using the high spec option */
  protected static final long ROCKSDB_MEMTABLE_SIZE_HIGH_SPEC = 1_073_741_824L;

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);
  private final ReadOptions readOptions = new ReadOptions().setVerifyChecksums(false);
  private final MetricsSystem metricsSystem;
  private final RocksDBMetricsFactory rocksDBMetricsFactory;
  private final RocksDBConfiguration configuration;
  private Map<Bytes, String> segmentsById;
  /** RocksDB DB options */
  protected DBOptions options;

  /** RocksDb transactionDB options */
  protected TransactionDBOptions txOptions;
  /** RocksDb statistics */
  protected final Statistics stats = new Statistics();

  /** RocksDB metrics */
  protected RocksDBMetrics metrics;

  /** Map of the columns handles by name */
  protected Map<String, RocksDbSegmentIdentifier> columnHandlesByName;
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
   * @param segments the segments
   * @param ignorableSegments the ignorable segments
   * @param metricsSystem the metrics system
   * @param rocksDBMetricsFactory the rocks db metrics factory
   * @throws StorageException the storage exception
   */
  public RocksDBColumnarKeyValueStorage(
      final RocksDBConfiguration configuration,
      final List<SegmentIdentifier> segments,
      final List<SegmentIdentifier> ignorableSegments,
      final MetricsSystem metricsSystem,
      final RocksDBMetricsFactory rocksDBMetricsFactory)
      throws StorageException {

    this.configuration = configuration;
    this.metricsSystem = metricsSystem;
    this.rocksDBMetricsFactory = rocksDBMetricsFactory;

    try {
      final ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
      trimmedSegments = new ArrayList<>(segments);
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
          trimmedSegments.stream()
              .map(
                  segment ->
                      new ColumnFamilyDescriptor(
                          segment.getId(),
                          new ColumnFamilyOptions()
                              .setTtl(0)
                              .setCompressionType(CompressionType.LZ4_COMPRESSION)
                              .setTableFormatConfig(createBlockBasedTableConfig(configuration))))
              .collect(Collectors.toList());
      columnDescriptors.add(
          new ColumnFamilyDescriptor(
              DEFAULT_COLUMN.getBytes(StandardCharsets.UTF_8),
              columnFamilyOptions
                  .setTtl(0)
                  .setCompressionType(CompressionType.LZ4_COMPRESSION)
                  .setTableFormatConfig(createBlockBasedTableConfig(configuration))));

      final Statistics stats = new Statistics();
      if (configuration.isHighSpec()) {
        options =
            new DBOptions()
                .setCreateIfMissing(true)
                .setMaxOpenFiles(configuration.getMaxOpenFiles())
                .setDbWriteBufferSize(ROCKSDB_MEMTABLE_SIZE_HIGH_SPEC)
                .setStatistics(stats)
                .setCreateMissingColumnFamilies(true)
                .setEnv(
                    Env.getDefault()
                        .setBackgroundThreads(configuration.getBackgroundThreadCount()));
      } else {
        options =
            new DBOptions()
                .setCreateIfMissing(true)
                .setMaxOpenFiles(configuration.getMaxOpenFiles())
                .setStatistics(stats)
                .setCreateMissingColumnFamilies(true)
                .setEnv(
                    Env.getDefault()
                        .setBackgroundThreads(configuration.getBackgroundThreadCount()));
      }

      txOptions = new TransactionDBOptions();
      columnHandles = new ArrayList<>(columnDescriptors.size());
    } catch (RocksDBException e) {
      throw new StorageException(e);
    }
  }

  void initMetrics() {
    metrics = rocksDBMetricsFactory.create(metricsSystem, configuration, getDB(), stats);
  }

  void initColumnHandler() throws RocksDBException {

    segmentsById =
        trimmedSegments.stream()
            .collect(
                Collectors.toMap(
                    segment -> Bytes.wrap(segment.getId()), SegmentIdentifier::getName));
    final ImmutableMap.Builder<String, RocksDbSegmentIdentifier> builder = ImmutableMap.builder();

    for (ColumnFamilyHandle columnHandle : columnHandles) {
      final String segmentName =
          requireNonNullElse(segmentsById.get(Bytes.wrap(columnHandle.getName())), DEFAULT_COLUMN);
      builder.put(segmentName, new RocksDbSegmentIdentifier(getDB(), columnHandle));
    }
    columnHandlesByName = builder.build();
  }

  BlockBasedTableConfig createBlockBasedTableConfig(final RocksDBConfiguration config) {
    if (config.isHighSpec()) return createBlockBasedTableConfigHighSpec();
    else return createBlockBasedTableConfigDefault(config);
  }

  private BlockBasedTableConfig createBlockBasedTableConfigHighSpec() {
    final LRUCache cache = new LRUCache(ROCKSDB_BLOCKCACHE_SIZE_HIGH_SPEC);
    return new BlockBasedTableConfig()
        .setFormatVersion(ROCKSDB_FORMAT_VERSION)
        .setBlockCache(cache)
        .setFilterPolicy(new BloomFilter(10, false))
        .setPartitionFilters(true)
        .setCacheIndexAndFilterBlocks(false)
        .setBlockSize(ROCKSDB_BLOCK_SIZE);
  }

  private BlockBasedTableConfig createBlockBasedTableConfigDefault(
      final RocksDBConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig()
        .setFormatVersion(ROCKSDB_FORMAT_VERSION)
        .setBlockCache(cache)
        .setFilterPolicy(new BloomFilter(10, false))
        .setPartitionFilters(true)
        .setCacheIndexAndFilterBlocks(false)
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
      return Optional.ofNullable(getDB().get(segment.get(), readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final RocksDbSegmentIdentifier segmentHandle) {
    final RocksIterator rocksIterator = getDB().newIterator(segmentHandle.get());
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  @Override
  public Stream<byte[]> streamKeys(final RocksDbSegmentIdentifier segmentHandle) {
    final RocksIterator rocksIterator = getDB().newIterator(segmentHandle.get());
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStreamKeys();
  }

  @Override
  public boolean tryDelete(final RocksDbSegmentIdentifier segmentHandle, final byte[] key) {
    try {
      getDB().delete(segmentHandle.get(), tryDeleteOptions, key);
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
    return stream(segmentHandle)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getKey)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(
      final RocksDbSegmentIdentifier segmentHandle, final Predicate<byte[]> returnCondition) {
    return stream(segmentHandle)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public void clear(final RocksDbSegmentIdentifier segmentHandle) {

    columnHandlesByName.values().stream()
        .filter(e -> e.equals(segmentHandle))
        .findAny()
        .ifPresent(RocksDbSegmentIdentifier::reset);
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
      getDB().close();
    }
  }

  void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed RocksDbKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  class RocksDbTransaction implements Transaction<RocksDbSegmentIdentifier> {

    private final org.rocksdb.Transaction innerTx;
    private final WriteOptions options;

    /**
     * Instantiates a new RocksDb transaction.
     *
     * @param innerTx the inner tx
     * @param options the write options
     */
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
    public synchronized void commit() throws StorageException {
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

  abstract RocksDB getDB();
}
