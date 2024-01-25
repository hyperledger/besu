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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.VersionedStorageFormat.BONSAI_WITH_VARIABLES;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.VersionedStorageFormat.FOREST_WITH_VARIABLES;

import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.VersionedStorageFormat;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.OptimisticRocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.TransactionDBRocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Rocks db key value storage factory creates segmented storage and uses a adapter to support
 * unsegmented keyvalue storage.
 */
public class RocksDBKeyValueStorageFactory implements KeyValueStorageFactory {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBKeyValueStorageFactory.class);
  private static final VersionedStorageFormat DEFAULT_VERSIONED_FORMAT =
      VersionedStorageFormat.FOREST_WITH_VARIABLES;
  private static final EnumSet<VersionedStorageFormat> SUPPORTED_VERSIONED_FORMATS =
      EnumSet.of(FOREST_WITH_VARIABLES, BONSAI_WITH_VARIABLES);
  private static final String NAME = "rocksdb";
  private final RocksDBMetricsFactory rocksDBMetricsFactory;
  private DatabaseMetadata databaseMetadata;
  private RocksDBColumnarKeyValueStorage segmentedStorage;
  private RocksDBConfiguration rocksDBConfiguration;

  private final Supplier<RocksDBFactoryConfiguration> configuration;
  private final List<SegmentIdentifier> configuredSegments;
  private final List<SegmentIdentifier> ignorableSegments;

  /**
   * Instantiates a new RocksDb key value storage factory.
   *
   * @param configuration the configuration
   * @param configuredSegments the segments
   * @param ignorableSegments the ignorable segments
   * @param format the storage format
   * @param rocksDBMetricsFactory the rocks db metrics factory
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> configuredSegments,
      final List<SegmentIdentifier> ignorableSegments,
      final DataStorageFormat format,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    this.configuration = configuration;
    this.configuredSegments = configuredSegments;
    this.ignorableSegments = ignorableSegments;
    this.rocksDBMetricsFactory = rocksDBMetricsFactory;
    this.databaseMetadata = DatabaseMetadata.defaultForNewDb(format);
  }

  /**
   * Instantiates a new RocksDb key value storage factory.
   *
   * @param configuration the configuration
   * @param configuredSegments the segments
   * @param format the storage format
   * @param rocksDBMetricsFactory the rocks db metrics factory
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> configuredSegments,
      final DataStorageFormat format,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    this(configuration, configuredSegments, List.of(), format, rocksDBMetricsFactory);
  }

  /**
   * Instantiates a new Rocks db key value storage factory.
   *
   * @param configuration the configuration
   * @param configuredSegments the segments
   * @param ignorableSegments the ignorable segments
   * @param rocksDBMetricsFactory the rocks db metrics factory
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> configuredSegments,
      final List<SegmentIdentifier> ignorableSegments,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    this(
        configuration,
        configuredSegments,
        ignorableSegments,
        DEFAULT_VERSIONED_FORMAT.getFormat(),
        rocksDBMetricsFactory);
  }

  /**
   * Instantiates a new Rocks db key value storage factory.
   *
   * @param configuration the configuration
   * @param configuredSegments the segments
   * @param rocksDBMetricsFactory the rocks db metrics factory
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> configuredSegments,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    this(
        configuration,
        configuredSegments,
        List.of(),
        DEFAULT_VERSIONED_FORMAT.getFormat(),
        rocksDBMetricsFactory);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public KeyValueStorage create(
      final SegmentIdentifier segment,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem)
      throws StorageException {
    return new SegmentedKeyValueStorageAdapter(
        segment, create(List.of(segment), commonConfiguration, metricsSystem));
  }

  @Override
  public SegmentedKeyValueStorage create(
      final List<SegmentIdentifier> segments,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem)
      throws StorageException {
    if (requiresInit()) {
      init(commonConfiguration);
    }

    // safety check to see that segments all exist within configured segments
    if (!configuredSegments.containsAll(segments)) {
      throw new StorageException(
          "Attempted to create storage for segments that are not configured: "
              + segments.stream()
                  .filter(segment -> !configuredSegments.contains(segment))
                  .map(SegmentIdentifier::toString)
                  .collect(Collectors.joining(", ")));
    }

    if (segmentedStorage == null) {
      final List<SegmentIdentifier> segmentsForFormat =
          configuredSegments.stream()
              .filter(
                  segmentId ->
                      segmentId.includeInDatabaseFormat(databaseMetadata.getVersionedStorageFormat().getFormat()))
              .toList();

      // It's probably a good idea for the creation logic to be entirely dependent on the database
      // version. Introducing intermediate booleans that represent database properties and
      // dispatching
      // creation logic based on them is error-prone.
      switch (databaseMetadata.getVersionedStorageFormat().getFormat()) {
        case FOREST -> {
          LOG.debug("FOREST mode detected, using TransactionDB.");
          segmentedStorage =
              new TransactionDBRocksDBColumnarKeyValueStorage(
                  rocksDBConfiguration,
                  segmentsForFormat,
                  ignorableSegments,
                  metricsSystem,
                  rocksDBMetricsFactory);
        }
        case BONSAI -> {
          LOG.debug("BONSAI mode detected, Using OptimisticTransactionDB.");
          segmentedStorage =
              new OptimisticRocksDBColumnarKeyValueStorage(
                  rocksDBConfiguration,
                  segmentsForFormat,
                  ignorableSegments,
                  metricsSystem,
                  rocksDBMetricsFactory);
        }
      }
    }
    return segmentedStorage;
  }

  /**
   * Storage path.
   *
   * @param commonConfiguration the common configuration
   * @return the path
   */
  protected Path storagePath(final BesuConfiguration commonConfiguration) {
    return commonConfiguration.getStoragePath();
  }

  private void init(final BesuConfiguration commonConfiguration) {
    try {
      databaseMetadata = readDatabaseMetadata(commonConfiguration);
    } catch (final IOException e) {
      final String message =
          "Failed to retrieve the RocksDB database meta version: "
              + e.getMessage()
              + " could not be found. You may not have the appropriate permission to access the item.";
      throw new StorageException(message, e);
    }
    rocksDBConfiguration =
        RocksDBConfigurationBuilder.from(configuration.get())
            .databaseDir(storagePath(commonConfiguration))
            .build();
  }

  private boolean requiresInit() {
    return segmentedStorage == null;
  }

  private DatabaseMetadata readDatabaseMetadata(final BesuConfiguration commonConfiguration)
      throws IOException {
    final Path dataDir = commonConfiguration.getDataPath();
    final boolean databaseExists = commonConfiguration.getStoragePath().toFile().exists();
    final boolean dataDirExists = dataDir.toFile().exists();
    final DatabaseMetadata databaseMetadata;
    if (databaseExists) {
      databaseMetadata = DatabaseMetadata.lookUpFrom(dataDir);
      LOG.info(
          "Existing database detected at {}. Metadata {}. Processing WAL...",
          dataDir,
          databaseMetadata);
    } else {
      databaseMetadata = DatabaseMetadata.defaultForNewDb(commonConfiguration.getDatabaseFormat());
      LOG.info("No existing database detected at {}. Using default metadata for new db {}", dataDir, databaseMetadata);
      if (!dataDirExists) {
        Files.createDirectories(dataDir);
      }
      databaseMetadata.writeToDirectory(dataDir);
    }

    if (!SUPPORTED_VERSIONED_FORMATS.contains(databaseMetadata.getVersionedStorageFormat())) {
      final String message = "Unsupported RocksDB metadata: " + databaseMetadata;
      LOG.error(message);
      throw new StorageException(message);
    }

    return databaseMetadata;
  }

  @Override
  public void close() throws IOException {
    if (segmentedStorage != null) {
      segmentedStorage.close();
    }
  }

  @Override
  public boolean isSegmentIsolationSupported() {
    return true;
  }

  @Override
  public boolean isSnapshotIsolationSupported() {
    return true;
  }
}
