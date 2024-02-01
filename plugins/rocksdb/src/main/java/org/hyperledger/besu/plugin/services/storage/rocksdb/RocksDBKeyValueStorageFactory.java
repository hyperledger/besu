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

import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.OptimisticRocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.TransactionDBRocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
  private static final int DEFAULT_VERSION = 1;
  private static final Set<Integer> SUPPORTED_VERSIONS = Set.of(1, 2);
  private static final String NAME = "rocksdb";
  private final RocksDBMetricsFactory rocksDBMetricsFactory;

  private final int defaultVersion;
  private Integer databaseVersion;
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
   * @param defaultVersion the default version
   * @param rocksDBMetricsFactory the rocks db metrics factory
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> configuredSegments,
      final List<SegmentIdentifier> ignorableSegments,
      final int defaultVersion,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    this.configuration = configuration;
    this.configuredSegments = configuredSegments;
    this.ignorableSegments = ignorableSegments;
    this.defaultVersion = defaultVersion;
    this.rocksDBMetricsFactory = rocksDBMetricsFactory;
  }

  /**
   * Instantiates a new RocksDb key value storage factory.
   *
   * @param configuration the configuration
   * @param configuredSegments the segments
   * @param defaultVersion the default version
   * @param rocksDBMetricsFactory the rocks db metrics factory
   */
  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> configuredSegments,
      final int defaultVersion,
      final RocksDBMetricsFactory rocksDBMetricsFactory) {
    this(configuration, configuredSegments, List.of(), defaultVersion, rocksDBMetricsFactory);
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
        DEFAULT_VERSION,
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
    this(configuration, configuredSegments, List.of(), DEFAULT_VERSION, rocksDBMetricsFactory);
  }

  /**
   * Gets default version.
   *
   * @return the default version
   */
  int getDefaultVersion() {
    return defaultVersion;
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
    final boolean isForestStorageFormat =
        DataStorageFormat.FOREST.getDatabaseVersion() == commonConfiguration.getDatabaseVersion();
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

    // It's probably a good idea for the creation logic to be entirely dependent on the database
    // version. Introducing intermediate booleans that represent database properties and dispatching
    // creation logic based on them is error-prone.
    switch (databaseVersion) {
      case 1, 2 -> {
        if (segmentedStorage == null) {
          final List<SegmentIdentifier> segmentsForVersion =
              configuredSegments.stream()
                  .filter(segmentId -> segmentId.includeInDatabaseVersion(databaseVersion))
                  .collect(Collectors.toList());
          if (isForestStorageFormat) {
            LOG.debug("FOREST mode detected, using TransactionDB.");
            segmentedStorage =
                new TransactionDBRocksDBColumnarKeyValueStorage(
                    rocksDBConfiguration,
                    segmentsForVersion,
                    ignorableSegments,
                    metricsSystem,
                    rocksDBMetricsFactory);
          } else {
            LOG.debug("Using OptimisticTransactionDB.");
            segmentedStorage =
                new OptimisticRocksDBColumnarKeyValueStorage(
                    rocksDBConfiguration,
                    segmentsForVersion,
                    ignorableSegments,
                    metricsSystem,
                    rocksDBMetricsFactory);
          }
        }
        return segmentedStorage;
      }
      default -> throw new IllegalStateException(
          String.format(
              "Developer error: A supported database version (%d) was detected but there is no associated creation logic.",
              databaseVersion));
    }
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
      databaseVersion = readDatabaseVersion(commonConfiguration);
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

  private int readDatabaseVersion(final BesuConfiguration commonConfiguration) throws IOException {
    final Path dataDir = commonConfiguration.getDataPath();
    final boolean databaseExists = commonConfiguration.getStoragePath().toFile().exists();
    final boolean dataDirExists = dataDir.toFile().exists();
    final int databaseVersion;
    if (databaseExists) {
      databaseVersion = DatabaseMetadata.lookUpFrom(dataDir).getVersion();
      if (databaseVersion != commonConfiguration.getDatabaseVersion()) {
        String error =
            String.format(
                "Mismatch detected: Database at %s is version '%s', but configuration expects version '%s'.",
                dataDir, databaseVersion, commonConfiguration.getDatabaseVersion());
        throw new StorageException(error);
      }
      LOG.info(
          "Existing database detected at {}. Version {}. Compacting database...",
          dataDir,
          databaseVersion);
    } else {
      databaseVersion = commonConfiguration.getDatabaseVersion();
      LOG.info("No existing database detected at {}. Using version {}", dataDir, databaseVersion);
      if (!dataDirExists) {
        Files.createDirectories(dataDir);
      }
      new DatabaseMetadata(databaseVersion).writeToDirectory(dataDir);
    }

    if (!SUPPORTED_VERSIONS.contains(databaseVersion)) {
      final String message = "Unsupported RocksDB Metadata version of: " + databaseVersion;
      LOG.error(message);
      throw new StorageException(message);
    }

    return databaseVersion;
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
