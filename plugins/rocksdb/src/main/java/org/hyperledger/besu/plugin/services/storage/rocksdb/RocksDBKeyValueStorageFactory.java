/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.unsegmented.RocksDBKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.google.common.base.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RocksDBKeyValueStorageFactory implements KeyValueStorageFactory {

  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_VERSION = 1;
  private static final Set<Integer> SUPPORTED_VERSION = Set.of(0, 1);
  private static final String NAME = "rocksdb";

  private boolean isSegmentIsolationSupported;
  private SegmentedKeyValueStorage<?> segmentedStorage;
  private KeyValueStorage unsegmentedStorage;

  private final Supplier<RocksDBFactoryConfiguration> configuration;
  private final List<SegmentIdentifier> segments;

  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments) {
    this.configuration = configuration;
    this.segments = segments;
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

    if (requiresInit()) {
      init(commonConfiguration, metricsSystem);
    }

    return isSegmentIsolationSupported
        ? new SegmentedKeyValueStorageAdapter<>(segment, segmentedStorage)
        : unsegmentedStorage;
  }

  @Override
  public boolean isSegmentIsolationSupported() {
    return isSegmentIsolationSupported;
  }

  public void close() throws IOException {
    if (segmentedStorage != null) {
      segmentedStorage.close();
    }
    if (unsegmentedStorage != null) {
      unsegmentedStorage.close();
    }
  }

  protected Path storagePath(final BesuConfiguration commonConfiguration) {
    return commonConfiguration.getStoragePath();
  }

  private boolean requiresInit() {
    return segmentedStorage == null && unsegmentedStorage == null;
  }

  private void init(
      final BesuConfiguration commonConfiguration, final MetricsSystem metricsSystem) {
    try {
      this.isSegmentIsolationSupported = databaseVersion(commonConfiguration) == DEFAULT_VERSION;
    } catch (final IOException e) {
      LOG.error("Failed to retrieve the RocksDB database meta version: {}", e.getMessage());
      throw new StorageException(e.getMessage(), e);
    }

    final RocksDBConfiguration rocksDBConfiguration =
        RocksDBConfigurationBuilder.from(configuration.get())
            .databaseDir(storagePath(commonConfiguration))
            .build();

    if (isSegmentIsolationSupported) {
      this.unsegmentedStorage = null;
      this.segmentedStorage =
          new RocksDBColumnarKeyValueStorage(rocksDBConfiguration, segments, metricsSystem);
    } else {
      this.unsegmentedStorage = new RocksDBKeyValueStorage(rocksDBConfiguration, metricsSystem);
      this.segmentedStorage = null;
    }
  }

  private int databaseVersion(final BesuConfiguration commonConfiguration) throws IOException {
    final Path databaseDir = storagePath(commonConfiguration);
    final boolean databaseExists = databaseDir.resolve("IDENTITY").toFile().exists();
    final int databaseVersion;
    if (databaseExists) {
      databaseVersion = DatabaseMetadata.fromDirectory(databaseDir).getVersion();
      LOG.info("Existing database detected at {}. Version {}", databaseDir, databaseVersion);
    } else {
      databaseVersion = DEFAULT_VERSION;
      LOG.info(
          "No existing database detected at {}. Using version {}", databaseDir, databaseVersion);
      Files.createDirectories(databaseDir);
      new DatabaseMetadata(databaseVersion).writeToDirectory(databaseDir);
    }

    if (!SUPPORTED_VERSION.contains(databaseVersion)) {
      final String message = "Unsupported RocksDB Metadata version of: " + databaseVersion;
      LOG.error(message);
      throw new StorageException(message);
    }

    return databaseVersion;
  }
}
