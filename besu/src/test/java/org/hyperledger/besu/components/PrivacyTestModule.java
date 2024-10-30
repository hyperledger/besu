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
package org.hyperledger.besu.components;

import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_IS_HIGH_SPEC;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;

import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;

@Module
public class PrivacyTestModule {

  @Provides
  @Named("dataDir")
  Path provideDataDir() {
    try {
      return Files.createTempDirectory("PrivacyTestDatadir");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Provides
  Vertx provideVertx() {
    return Vertx.vertx();
  }

  @Provides
  DataStorageConfiguration provideDataStorageConfiguration() {
    return DataStorageConfiguration.DEFAULT_FOREST_CONFIG;
  }

  @Provides
  @Singleton
  @Named("dbDir")
  Path provideDbDir(@Named("dataDir") final Path dataDir) {
    try {
      final Path dbDir = Files.createTempDirectory(dataDir, "database");
      return dbDir;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Provides
  @Singleton
  @Named("flexibleEnabled")
  Boolean provideFlexibleEnabled() {
    return true;
  }

  @Provides
  @Singleton
  @SuppressWarnings("CloseableProvides")
  PrivacyStorageProvider provideKeyValueStorageProvider(
      @Named("dbDir") final Path dbDir,
      final DataStorageConfiguration dataStorageConfiguration,
      @Named("dataDir") final Path dataDir) {
    final var besuConfiguration = new BesuConfigurationImpl();
    besuConfiguration.init(dataDir, dbDir, dataStorageConfiguration);
    return new PrivacyKeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValuePrivacyStorageFactory(
                new RocksDBKeyValueStorageFactory(
                    () ->
                        new RocksDBFactoryConfiguration(
                            DEFAULT_MAX_OPEN_FILES,
                            DEFAULT_BACKGROUND_THREAD_COUNT,
                            DEFAULT_CACHE_CAPACITY,
                            DEFAULT_IS_HIGH_SPEC),
                    Arrays.asList(KeyValueSegmentIdentifier.values()),
                    RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS)))
        .withCommonConfiguration(besuConfiguration)
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }
}
