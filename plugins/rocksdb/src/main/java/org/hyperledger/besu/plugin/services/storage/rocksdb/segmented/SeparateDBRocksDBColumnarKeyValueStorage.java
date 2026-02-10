/*
 * Copyright contributors to Hyperledger Besu.
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
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetrics;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbIterator;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbUtil;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageTransactionValidatorDecorator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.AbstractRocksIterator;
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
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocksDB Columnar storage with separate database instance per column.
 * 
 * <p>Instead of using column families within a single RocksDB instance, this implementation
 * creates a separate RocksDB database for each segment/column. This provides better isolation
 * and independent configuration per segment.
 */
public class SeparateDBRocksDBColumnarKeyValueStorage implements SegmentedKeyValueStorage {

  private static final Logger LOG =
      LoggerFactory.getLogger(SeparateDBRocksDBColumnarKeyValueStorage.class);
  
  private static final int ROCKSDB_FORMAT_VERSION = 5;
  private static final long ROCKSDB_BLOCK_SIZE = 32768;
  protected static final long ROCKSDB_BLOCKCACHE_SIZE_HIGH_SPEC = 1_073_741_824L;
  protected static final long WAL_MAX_TOTAL_SIZE = 1_073_741_824L;
  protected static final long EXPECTED_WAL_FILE_SIZE = 67_108_864L;
  private static final long NUMBER_OF_LOG_FILES_TO_KEEP = 7;
  private static final long TIME_TO_ROLL_LOG_FILE = 86_400L;

  static {
    RocksDbUtil.loadNativeLibrary();
  }

  protected final AtomicBoolean closed = new AtomicBoolean(false);
  private final WriteOptions tryDeleteOptions =
      new WriteOptions().setNoSlowdown(true).setIgnoreMissingColumnFamilies(true);
  private final ReadOptions readOptions = new ReadOptions().setVerifyChecksums(false);
  
  protected final RocksDBConfiguration configuration;
  private final MetricsSystem metricsSystem;
  private final RocksDBMetricsFactory rocksDBMetricsFactory;
  
  /** Map of RocksDB instances per segment */
  private final Map<SegmentIdentifier, TransactionDB> databases = new HashMap<>();
  
  /** Map of default column handles per segment */
  private final Map<SegmentIdentifier, ColumnFamilyHandle> defaultColumnHandles = new HashMap<>();
  
  /** Map of metrics per segment */
  private final Map<SegmentIdentifier, RocksDBMetrics> segmentMetrics = new HashMap<>();
  
  /** Map of statistics per segment */
  private final Map<SegmentIdentifier, Statistics> segmentStats = new HashMap<>();

  /**
   * Instantiates a new Separate DB RocksDB columnar key value storage.
   *
   * @param configuration the configuration
   * @param segments the segments to create separate databases for
   * @param ignorableSegments the ignorable segments (not used in this implementation)
   * @param metricsSystem the metrics system
   * @param rocksDBMetricsFactory the rocks db metrics factory
   * @throws StorageException the storage exception
   */
  public SeparateDBRocksDBColumnarKeyValueStorage(
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
      // Create a separate database for each segment
      for (SegmentIdentifier segment : segments) {
        createDatabaseForSegment(segment);
      }
    } catch (RocksDBException e) {
      // Close any opened databases before throwing
      close();
      throw new StorageException("Failed to initialize separate RocksDB instances", e);
    } catch (StorageException e) {
      // Close any opened databases before throwing
      close();
      throw e;
    }
  }

  /**
   * Creates a separate RocksDB database for a specific segment.
   *
   * @param segment the segment identifier
   * @throws RocksDBException if database creation fails
   */
  private void createDatabaseForSegment(final SegmentIdentifier segment)
      throws RocksDBException {
    
    // Create a subdirectory for this segment using the segment name
    Path segmentPath = configuration.getDatabaseDir().resolve(segment.getName());
    String dbPath = segmentPath.toString();
    
    LOG.info("Creating separate RocksDB instance for segment '{}' at {}", 
        segment.getName(), dbPath);

    // Create the directory if it doesn't exist
    try {
      java.nio.file.Files.createDirectories(segmentPath);
    } catch (java.io.IOException e) {
      throw new StorageException("Failed to create directory for segment: " + segment.getName(), e);
    }

    // Create options for this segment's database
    Statistics stats = new Statistics();
    segmentStats.put(segment, stats);
    
    DBOptions dbOptions = createDBOptions(stats);
    TransactionDBOptions txOptions = new TransactionDBOptions();
    
    // Create column family options for the default column family
    ColumnFamilyOptions cfOptions = createColumnFamilyOptions(segment);
    
    // Create column family descriptor for default column
    ColumnFamilyDescriptor defaultCfDescriptor = 
        new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions);
    
    java.util.List<ColumnFamilyDescriptor> cfDescriptors = 
        java.util.List.of(defaultCfDescriptor);
    java.util.List<ColumnFamilyHandle> cfHandles = new java.util.ArrayList<>();
    
    // Open the database
    TransactionDB db = RocksDBOpener.openTransactionDBWithWarning(
        dbOptions, txOptions, dbPath, cfDescriptors, cfHandles);
    
    databases.put(segment, db);
    defaultColumnHandles.put(segment, cfHandles.get(0));
    
    // Initialize metrics for this segment
    RocksDBMetrics metrics = rocksDBMetricsFactory.create(
        metricsSystem, configuration, db, stats);
    segmentMetrics.put(segment, metrics);
    
    LOG.debug("Successfully created RocksDB instance for segment '{}'", segment.getName());
  }

  /**
   * Creates DBOptions for a segment database.
   *
   * @param stats the statistics object
   * @return configured DBOptions
   */
  private DBOptions createDBOptions(final Statistics stats) {
    DBOptions options = new DBOptions();
    options
        .setCreateIfMissing(true)
        .setMaxOpenFiles(configuration.getMaxOpenFiles())
        .setStatistics(stats)
        .setCreateMissingColumnFamilies(true)
        .setLogFileTimeToRoll(TIME_TO_ROLL_LOG_FILE)
        .setKeepLogFileNum(NUMBER_OF_LOG_FILES_TO_KEEP)
        .setEnv(Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()))
        .setMaxTotalWalSize(WAL_MAX_TOTAL_SIZE)
        .setRecycleLogFileNum(WAL_MAX_TOTAL_SIZE / EXPECTED_WAL_FILE_SIZE);
    return options;
  }

  /**
   * Creates ColumnFamilyOptions for a segment.
   *
   * @param segment the segment identifier
   * @return configured ColumnFamilyOptions
   */
  private ColumnFamilyOptions createColumnFamilyOptions(final SegmentIdentifier segment) {
    BlockBasedTableConfig tableConfig = createBlockBasedTableConfig(segment);
    
    ColumnFamilyOptions options = new ColumnFamilyOptions()
        .setTtl(0)
        .setCompressionType(CompressionType.LZ4_COMPRESSION)
        .setTableFormatConfig(tableConfig)
        .setLevelCompactionDynamicLevelBytes(true);
    
    // Configure BlobDB for segments with static data
    if (segment.containsStaticData()) {
      configureBlobDB(segment, options);
    }
    
    return options;
  }

  /**
   * Creates BlockBasedTableConfig for a segment.
   *
   * @param segment the segment identifier
   * @return configured BlockBasedTableConfig
   */
  private BlockBasedTableConfig createBlockBasedTableConfig(final SegmentIdentifier segment) {
    final LRUCache cache = new LRUCache(
        configuration.isHighSpec() && segment.isEligibleToHighSpecFlag()
            ? ROCKSDB_BLOCKCACHE_SIZE_HIGH_SPEC
            : configuration.getCacheCapacity());
    
    return new BlockBasedTableConfig()
        .setFormatVersion(ROCKSDB_FORMAT_VERSION)
        .setBlockCache(cache)
        .setFilterPolicy(new BloomFilter(10, false))
        .setPartitionFilters(true)
        .setCacheIndexAndFilterBlocks(false)
        .setBlockSize(ROCKSDB_BLOCK_SIZE);
  }

  /**
   * Configures BlobDB settings for segments with static data.
   *
   * @param segment the segment identifier
   * @param options the column family options to configure
   */
  private void configureBlobDB(
      final SegmentIdentifier segment, 
      final ColumnFamilyOptions options) {
    options
        .setEnableBlobFiles(true)
        .setEnableBlobGarbageCollection(segment.isStaticDataGarbageCollectionEnabled())
        .setMinBlobSize(100)
        .setBlobCompressionType(CompressionType.LZ4_COMPRESSION);
    
    if (configuration.getBlobGarbageCollectionAgeCutoff().isPresent()) {
      options.setBlobGarbageCollectionAgeCutoff(
          configuration.getBlobGarbageCollectionAgeCutoff().get());
    }
    if (configuration.getBlobGarbageCollectionForceThreshold().isPresent()) {
      options.setBlobGarbageCollectionForceThreshold(
          configuration.getBlobGarbageCollectionForceThreshold().get());
    }
  }

  /**
   * Gets the database instance for a segment.
   *
   * @param segment the segment identifier
   * @return the RocksDB instance
   */
  private TransactionDB getDatabase(final SegmentIdentifier segment) {
    TransactionDB db = databases.get(segment);
    if (db == null) {
      throw new IllegalArgumentException(
          "No database found for segment: " + segment.getName());
    }
    return db;
  }

  /**
   * Gets the default column handle for a segment's database.
   *
   * @param segment the segment identifier
   * @return the column family handle
   */
  private ColumnFamilyHandle getColumnHandle(final SegmentIdentifier segment) {
    ColumnFamilyHandle handle = defaultColumnHandles.get(segment);
    if (handle == null) {
      throw new IllegalArgumentException(
          "No column handle found for segment: " + segment.getName());
    }
    return handle;
  }

  /**
   * Gets the metrics for a segment.
   *
   * @param segment the segment identifier
   * @return the RocksDB metrics
   */
  private RocksDBMetrics getMetrics(final SegmentIdentifier segment) {
    RocksDBMetrics metrics = segmentMetrics.get(segment);
    if (metrics == null) {
      throw new IllegalArgumentException(
          "No metrics found for segment: " + segment.getName());
    }
    return metrics;
  }

  @Override
  public Optional<byte[]> get(final SegmentIdentifier segment, final byte[] key)
      throws StorageException {
    throwIfClosed();
    
    try (final OperationTimer.TimingContext ignored = 
        getMetrics(segment).getReadLatency().startTimer()) {
      TransactionDB db = getDatabase(segment);
      ColumnFamilyHandle handle = getColumnHandle(segment);
      return Optional.ofNullable(db.get(handle, readOptions, key));
    } catch (final RocksDBException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public Optional<NearestKeyValue> getNearestBefore(
      final SegmentIdentifier segment, final Bytes key) throws StorageException {
    throwIfClosed();
    
    TransactionDB db = getDatabase(segment);
    ColumnFamilyHandle handle = getColumnHandle(segment);
    
    try (final RocksIterator rocksIterator = db.newIterator(handle)) {
      rocksIterator.seekForPrev(key.toArrayUnsafe());
      return Optional.of(rocksIterator)
          .filter(AbstractRocksIterator::isValid)
          .map(it -> new NearestKeyValue(Bytes.of(it.key()), Optional.of(it.value())));
    }
  }

  @Override
  public Optional<NearestKeyValue> getNearestAfter(
      final SegmentIdentifier segment, final Bytes key) throws StorageException {
    throwIfClosed();
    
    TransactionDB db = getDatabase(segment);
    ColumnFamilyHandle handle = getColumnHandle(segment);
    
    try (final RocksIterator rocksIterator = db.newIterator(handle)) {
      rocksIterator.seek(key.toArrayUnsafe());
      return Optional.of(rocksIterator)
          .filter(AbstractRocksIterator::isValid)
          .map(it -> new NearestKeyValue(Bytes.of(it.key()), Optional.of(it.value())));
    }
  }

  @Override
  public Stream<Pair<byte[], byte[]>> stream(final SegmentIdentifier segment) {
    throwIfClosed();
    
    TransactionDB db = getDatabase(segment);
    ColumnFamilyHandle handle = getColumnHandle(segment);
    final RocksIterator rocksIterator = db.newIterator(handle);
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey) {
    throwIfClosed();
    
    TransactionDB db = getDatabase(segment);
    ColumnFamilyHandle handle = getColumnHandle(segment);
    final RocksIterator rocksIterator = db.newIterator(handle);
    rocksIterator.seek(startKey);
    return RocksDbIterator.create(rocksIterator).toStream();
  }

  @Override
  public Stream<Pair<byte[], byte[]>> streamFromKey(
      final SegmentIdentifier segment, final byte[] startKey, final byte[] endKey) {
    throwIfClosed();
    
    final Bytes endKeyBytes = Bytes.wrap(endKey);
    TransactionDB db = getDatabase(segment);
    ColumnFamilyHandle handle = getColumnHandle(segment);
    final RocksIterator rocksIterator = db.newIterator(handle);
    rocksIterator.seek(startKey);
    return RocksDbIterator.create(rocksIterator)
        .toStream()
        .takeWhile(e -> endKeyBytes.compareTo(Bytes.wrap(e.getKey())) >= 0);
  }

  @Override
  public Stream<byte[]> streamKeys(final SegmentIdentifier segment) {
    throwIfClosed();
    
    TransactionDB db = getDatabase(segment);
    ColumnFamilyHandle handle = getColumnHandle(segment);
    final RocksIterator rocksIterator = db.newIterator(handle);
    rocksIterator.seekToFirst();
    return RocksDbIterator.create(rocksIterator).toStreamKeys();
  }

  @Override
  public boolean tryDelete(final SegmentIdentifier segment, final byte[] key) {
    throwIfClosed();
    
    try {
      TransactionDB db = getDatabase(segment);
      ColumnFamilyHandle handle = getColumnHandle(segment);
      db.delete(handle, tryDeleteOptions, key);
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
      final SegmentIdentifier segment, final Predicate<byte[]> returnCondition) {
    return stream(segment)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getKey)
        .collect(toUnmodifiableSet());
  }

  @Override
  public Set<byte[]> getAllValuesFromKeysThat(
      final SegmentIdentifier segment, final Predicate<byte[]> returnCondition) {
    return stream(segment)
        .filter(pair -> returnCondition.test(pair.getKey()))
        .map(Pair::getValue)
        .collect(toUnmodifiableSet());
  }

  @Override
  public void clear(final SegmentIdentifier segment) {
    throwIfClosed();
    
    try {
      TransactionDB db = getDatabase(segment);
      ColumnFamilyHandle handle = getColumnHandle(segment);
      
      // Delete all keys in this segment
      // We cannot drop the DEFAULT column family, so we iterate and delete all keys
      try (final RocksIterator iterator = db.newIterator(handle)) {
        iterator.seekToFirst();
        while (iterator.isValid()) {
          db.delete(handle, iterator.key());
          iterator.next();
        }
      }
    } catch (RocksDBException e) {
      throw new StorageException("Failed to clear segment: " + segment.getName(), e);
    }
  }

  @Override
  public SegmentedKeyValueStorageTransaction startTransaction() throws StorageException {
    throwIfClosed();
    
    return new SegmentedKeyValueStorageTransactionValidatorDecorator(
        new SeparateDBRocksDBTransaction(), this.closed::get);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      LOG.info("Closing {} separate RocksDB instances", databases.size());
      
      // Close all column handles
      defaultColumnHandles.values().forEach(ColumnFamilyHandle::close);
      
      // Close all databases
      databases.values().forEach(TransactionDB::close);
      
      // Clear collections
      databases.clear();
      defaultColumnHandles.clear();
      segmentMetrics.clear();
      segmentStats.clear();
      
      tryDeleteOptions.close();
      readOptions.close();
    }
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  void throwIfClosed() {
    if (closed.get()) {
      LOG.error("Attempting to use a closed SeparateDBRocksDBColumnarKeyValueStorage");
      throw new IllegalStateException("Storage has been closed");
    }
  }

  /**
   * Transaction implementation for separate database architecture.
   */
  private class SeparateDBRocksDBTransaction implements SegmentedKeyValueStorageTransaction {
    
    private final Map<SegmentIdentifier, org.rocksdb.Transaction> transactions = new HashMap<>();
    private final Map<SegmentIdentifier, WriteOptions> writeOptions = new HashMap<>();
    
    SeparateDBRocksDBTransaction() {
      // Transactions are created lazily per segment when needed
    }
    
    private org.rocksdb.Transaction getTransaction(final SegmentIdentifier segment) {
      return transactions.computeIfAbsent(segment, seg -> {
        WriteOptions wo = new WriteOptions();
        wo.setIgnoreMissingColumnFamilies(true);
        writeOptions.put(seg, wo);
        return getDatabase(seg).beginTransaction(wo);
      });
    }
    
    @Override
    public void put(final SegmentIdentifier segment, final byte[] key, final byte[] value) {
      try {
        org.rocksdb.Transaction tx = getTransaction(segment);
        ColumnFamilyHandle handle = getColumnHandle(segment);
        tx.put(handle, key, value);
      } catch (RocksDBException e) {
        throw new StorageException(e);
      }
    }
    
    @Override
    public void remove(final SegmentIdentifier segment, final byte[] key) {
      try {
        org.rocksdb.Transaction tx = getTransaction(segment);
        ColumnFamilyHandle handle = getColumnHandle(segment);
        tx.delete(handle, key);
      } catch (RocksDBException e) {
        throw new StorageException(e);
      }
    }
    
    @Override
    public void commit() throws StorageException {
      try {
        // Commit all transactions
        for (org.rocksdb.Transaction tx : transactions.values()) {
          tx.commit();
        }
      } catch (RocksDBException e) {
        throw new StorageException(e);
      } finally {
        close();
      }
    }
    
    @Override
    public void rollback() {
      try {
        // Rollback all transactions
        for (org.rocksdb.Transaction tx : transactions.values()) {
          tx.rollback();
        }
      } catch (RocksDBException e) {
        LOG.error("Failed to rollback transaction", e);
      } finally {
        close();
      }
    }
    
    @Override
    public void close() {
      transactions.values().forEach(org.rocksdb.Transaction::close);
      writeOptions.values().forEach(WriteOptions::close);
      transactions.clear();
      writeOptions.clear();
    }
  }
}
