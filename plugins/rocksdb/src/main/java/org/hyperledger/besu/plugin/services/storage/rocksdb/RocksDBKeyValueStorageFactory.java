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

import static com.google.common.base.Preconditions.checkNotNull;

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
  private final int DEFAULT_VERSION;
  private static final Set<Integer> SUPPORTED_VERSIONS = Set.of(0, 1);
  private static final String NAME = "rocksdb";

  private Integer databaseVersion;
  private Boolean isSegmentIsolationSupported;
  private SegmentedKeyValueStorage<?> segmentedStorage;
  private KeyValueStorage unsegmentedStorage;
  private RocksDBConfiguration rocksDBConfiguration;

  private final Supplier<RocksDBFactoryConfiguration> configuration;
  private final List<SegmentIdentifier> segments;

  RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments,
      final int DEFAULT_VERSION) {
    this.configuration = configuration;
    this.segments = segments;
    this.DEFAULT_VERSION = DEFAULT_VERSION;
  }

  public RocksDBKeyValueStorageFactory(
      final Supplier<RocksDBFactoryConfiguration> configuration,
      final List<SegmentIdentifier> segments) {
    this(
        configuration,
        segments,
        /** Source of truth for the default database version. */
        1);
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
      init(commonConfiguration);
    }

    // It's probably a good idea for the creation logic to be entirely dependent on the database
    // version. Introducing intermediate booleans that represent database properties and dispatching
    // creation logic based on them is error prone.
    switch (databaseVersion) {
      case 0:
        {
          segmentedStorage = null;
          if (unsegmentedStorage == null) {
            unsegmentedStorage = new RocksDBKeyValueStorage(rocksDBConfiguration, metricsSystem);
          }
          return unsegmentedStorage;
        }
      case 1:
        {
          unsegmentedStorage = null;
          if (segmentedStorage == null) {
            segmentedStorage =
                new RocksDBColumnarKeyValueStorage(rocksDBConfiguration, segments, metricsSystem);
          }
          return new SegmentedKeyValueStorageAdapter<>(segment, segmentedStorage);
        }
      default:
        {
          throw new IllegalStateException(
              String.format(
                  "Developer error: A supported database version (%d) was detected but there is no associated creation logic.",
                  databaseVersion));
        }
    }
  }
  protected Path storagePath(final BesuConfiguration commonConfiguration) {
	    return commonConfiguration.getStoragePath();
	  }
  private void init(final BesuConfiguration commonConfiguration) {
    try {
      databaseVersion = readDatabaseVersion(commonConfiguration);
    } catch (final IOException e) {
      LOG.error("Failed to retrieve the RocksDB database meta version: {}", e.getMessage());
      throw new StorageException(e.getMessage(), e);
    }
    isSegmentIsolationSupported = databaseVersion >= 1;
    rocksDBConfiguration =
        RocksDBConfigurationBuilder.from(configuration.get())
            .databaseDir(storagePath(commonConfiguration))
            .build();
  }

  private boolean requiresInit() {
    return segmentedStorage == null && unsegmentedStorage == null;
  }

  private int readDatabaseVersion(final BesuConfiguration commonConfiguration) throws IOException {
    final Path databaseDir = commonConfiguration.getStoragePath();
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

    if (!SUPPORTED_VERSIONS.contains(databaseVersion)) {
      final String message = "Unsupported RocksDB Metadata version of: " + databaseVersion;
      LOG.error(message);
      throw new StorageException(message);
    }

    return databaseVersion;
  }

  @Override
  public void close() throws IOException {
    if (unsegmentedStorage != null) {
      unsegmentedStorage.close();
    }
    if (segmentedStorage != null) {
      segmentedStorage.close();
    }
  }

  @Override
  public boolean isSegmentIsolationSupported() {
    return checkNotNull(
        isSegmentIsolationSupported,
        "Whether segment isolation is supported will be determined during creation. Call a creation method first");
  }
}
