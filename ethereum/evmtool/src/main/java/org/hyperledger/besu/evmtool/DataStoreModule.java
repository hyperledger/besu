/*
 * Copyright ConsenSys AG.
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
 *
 */
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.LimitedInMemoryKeyValueStorage;

import java.util.List;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.base.Suppliers;
import dagger.Module;
import dagger.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"CloseableProvides"})
@Module(includes = GenesisFileModule.class)
public class DataStoreModule {

  private static final Logger LOG = LoggerFactory.getLogger(DataStoreModule.class);
  private final Supplier<RocksDBKeyValueStorageFactory> rocksDBFactory =
      Suppliers.memoize(
          () ->
              new RocksDBKeyValueStorageFactory(
                  RocksDBCLIOptions.create()::toDomainObject,
                  List.of(KeyValueSegmentIdentifier.values()),
                  RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS));

  @Provides
  @Singleton
  @Named("blockchain")
  KeyValueStorage provideBlockchainKeyValueStorage(
      @Named("KeyValueStorageName") final String keyValueStorageName,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem) {
    return constructKeyValueStorage(
        keyValueStorageName,
        commonConfiguration,
        metricsSystem,
        KeyValueSegmentIdentifier.BLOCKCHAIN);
  }

  @Provides
  @Singleton
  @Named("worldState")
  KeyValueStorage provideWorldStateKeyValueStorage(
      @Named("KeyValueStorageName") final String keyValueStorageName,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem) {
    return constructKeyValueStorage(
        keyValueStorageName,
        commonConfiguration,
        metricsSystem,
        KeyValueSegmentIdentifier.WORLD_STATE);
  }

  @Provides
  @Singleton
  @Named("worldStatePreimage")
  KeyValueStorage provideWorldStatePreimageKeyValueStorage(
      @Named("KeyValueStorageName") final String keyValueStorageName,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem) {
    return new LimitedInMemoryKeyValueStorage(5000);
  }

  @Provides
  @Singleton
  @Named("pruning")
  KeyValueStorage providePruningKeyValueStorage(
      @Named("KeyValueStorageName") final String keyValueStorageName,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem) {
    return constructKeyValueStorage(
        keyValueStorageName,
        commonConfiguration,
        metricsSystem,
        KeyValueSegmentIdentifier.PRUNING_STATE);
  }

  private KeyValueStorage constructKeyValueStorage(
      @Named("KeyValueStorageName") final String keyValueStorageName,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem,
      final SegmentIdentifier segment) {

    switch (keyValueStorageName) {
      case "rocksdb":
        return rocksDBFactory.get().create(segment, commonConfiguration, metricsSystem);
      default:
        LOG.error("Unknown key, continuing as though 'memory' was specified");
        // fall through
      case "memory":
        return new InMemoryKeyValueStorage();
    }
  }

  @Provides
  @Singleton
  static BlockchainStorage provideBlockchainStorage(
      @Named("blockchain") final KeyValueStorage keyValueStorage,
      final BlockHeaderFunctions blockHashFunction) {
    return new KeyValueStoragePrefixedKeyBlockchainStorage(keyValueStorage, blockHashFunction);
  }
}
