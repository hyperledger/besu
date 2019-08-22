/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import static java.util.AbstractMap.SimpleEntry;
import static java.util.Arrays.asList;

import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.services.kvstore.ColumnarRocksDbKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.LimitedInMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.services.kvstore.RocksDbKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage.Segment;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RocksDbStorageProvider {
  public static long DEFAULT_WORLD_STATE_PREIMAGE_CACHE_SIZE = 5_000L;
  private static final Logger LOG = LogManager.getLogger();
  public static final int DEFAULT_VERSION = 1;
  /** This key is the version and the value is the function used to create or load the database. */
  private static final TreeMap<Integer, StorageProviderFunction> PROVIDERS_BY_VERSION =
      new TreeMap<>(
          Map.ofEntries(
              new SimpleEntry<>(0, RocksDbStorageProvider::ofUnsegmented),
              new SimpleEntry<>(1, RocksDbStorageProvider::ofSegmented)));

  public static StorageProvider create(
      final RocksDbConfiguration rocksDbConfiguration, final MetricsSystem metricsSystem)
      throws IOException {
    return create(rocksDbConfiguration, metricsSystem, DEFAULT_WORLD_STATE_PREIMAGE_CACHE_SIZE);
  }

  public static StorageProvider create(
      final RocksDbConfiguration rocksDbConfiguration,
      final MetricsSystem metricsSystem,
      final long worldStatePreimageCacheSize)
      throws IOException {

    final Path databaseDir = rocksDbConfiguration.getDatabaseDir();
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

    final StorageProviderFunction providerFunction =
        Optional.ofNullable(PROVIDERS_BY_VERSION.get(databaseVersion))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Invalid database version %d. Valid versions are: %s. Default version is %d",
                            databaseVersion,
                            PROVIDERS_BY_VERSION.navigableKeySet().toString(),
                            DEFAULT_VERSION)));

    return providerFunction.apply(rocksDbConfiguration, metricsSystem, worldStatePreimageCacheSize);
  }

  private static StorageProvider ofUnsegmented(
      final RocksDbConfiguration rocksDbConfiguration,
      final MetricsSystem metricsSystem,
      final long worldStatePreimageCacheSize) {
    final KeyValueStorage kv = RocksDbKeyValueStorage.create(rocksDbConfiguration, metricsSystem);
    final KeyValueStorage preimageKv =
        new LimitedInMemoryKeyValueStorage(worldStatePreimageCacheSize);
    return new KeyValueStorageProvider(kv, kv, preimageKv, kv, kv, kv, false);
  }

  private static StorageProvider ofSegmented(
      final RocksDbConfiguration rocksDbConfiguration,
      final MetricsSystem metricsSystem,
      final long worldStatePreimageCacheSize) {

    final SegmentedKeyValueStorage<?> columnarStorage =
        ColumnarRocksDbKeyValueStorage.create(
            rocksDbConfiguration, asList(RocksDbSegment.values()), metricsSystem);
    final KeyValueStorage preimageStorage =
        new LimitedInMemoryKeyValueStorage(worldStatePreimageCacheSize);

    return new KeyValueStorageProvider(
        new SegmentedKeyValueStorageAdapter<>(RocksDbSegment.BLOCKCHAIN, columnarStorage),
        new SegmentedKeyValueStorageAdapter<>(RocksDbSegment.WORLD_STATE, columnarStorage),
        preimageStorage,
        new SegmentedKeyValueStorageAdapter<>(RocksDbSegment.PRIVATE_TRANSACTIONS, columnarStorage),
        new SegmentedKeyValueStorageAdapter<>(RocksDbSegment.PRIVATE_STATE, columnarStorage),
        new SegmentedKeyValueStorageAdapter<>(RocksDbSegment.PRUNING_STATE, columnarStorage),
        true);
  }

  private enum RocksDbSegment implements Segment {
    BLOCKCHAIN((byte) 1),
    WORLD_STATE((byte) 2),
    PRIVATE_TRANSACTIONS((byte) 3),
    PRIVATE_STATE((byte) 4),
    PRUNING_STATE((byte) 5);

    private final byte[] id;

    RocksDbSegment(final byte... id) {
      this.id = id;
    }

    @Override
    public String getName() {
      return name();
    }

    @Override
    public byte[] getId() {
      return id;
    }
  }

  private interface StorageProviderFunction {
    StorageProvider apply(
        final RocksDbConfiguration rocksDbConfiguration,
        final MetricsSystem metricsSystem,
        final long worldStatePreimageCacheSize)
        throws IOException;
  }
}
