/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageTransactionTransitionValidatorDecorator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.WriteOptions;

public class OptimisticRocksDBColumnarKeyValueStorage extends RocksDBColumnarKeyValueStorage {
  private final OptimisticTransactionDB db;

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
  public OptimisticRocksDBColumnarKeyValueStorage(
      final RocksDBConfiguration configuration,
      final List<SegmentIdentifier> segments,
      final List<SegmentIdentifier> ignorableSegments,
      final MetricsSystem metricsSystem,
      final RocksDBMetricsFactory rocksDBMetricsFactory)
      throws StorageException {
    super(configuration, segments, ignorableSegments, metricsSystem, rocksDBMetricsFactory);
    try (final ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()) {
      final List<SegmentIdentifier> trimmedSegments = new ArrayList<>(segments);
      final List<byte[]> existingColumnFamilies =
          RocksDB.listColumnFamilies(new Options(), configuration.getDatabaseDir().toString());
      // Only ignore if not existed currently
      ignorableSegments.stream()
          .filter(
              ignorableSegment ->
                  existingColumnFamilies.stream()
                      .noneMatch(existed -> Arrays.equals(existed, ignorableSegment.getId())))
          .forEach(trimmedSegments::remove);
      final List<ColumnFamilyDescriptor> columnDescriptors =
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
                .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
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
                .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
                .setStatistics(stats)
                .setCreateMissingColumnFamilies(true)
                .setEnv(
                    Env.getDefault()
                        .setBackgroundThreads(configuration.getBackgroundThreadCount()));
      }

      txOptions = new TransactionDBOptions();
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      db =
          OptimisticTransactionDB.open(
              options, configuration.getDatabaseDir().toString(), columnDescriptors, columnHandles);
      metrics = rocksDBMetricsFactory.create(metricsSystem, configuration, db, stats);
      final Map<Bytes, String> segmentsById =
          trimmedSegments.stream()
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

  @Override
  RocksDB getDB() {
    return db;
  }

  /**
   * Start a transaction
   *
   * @return the new transaction started
   * @throws StorageException the storage exception
   */
  @Override
  public SegmentedKeyValueStorage.Transaction<RocksDbSegmentIdentifier> startTransaction()
      throws StorageException {
    throwIfClosed();
    final WriteOptions writeOptions = new WriteOptions();
    writeOptions.setIgnoreMissingColumnFamilies(true);
    return new SegmentedKeyValueStorageTransactionTransitionValidatorDecorator<>(
        new RocksDBColumnarKeyValueStorage.RocksDbTransaction(
            db.beginTransaction(writeOptions), writeOptions));
  }

  /**
   * Take snapshot RocksDb columnar key value snapshot.
   *
   * @param segment the segment
   * @return the RocksDb columnar key value snapshot
   * @throws StorageException the storage exception
   */
  @Override
  public RocksDBColumnarKeyValueSnapshot takeSnapshot(final RocksDbSegmentIdentifier segment)
      throws StorageException {
    throwIfClosed();
    return new RocksDBColumnarKeyValueSnapshot(db, segment, metrics);
  }
}
