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
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.BLOCKCHAIN;
import static tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.PRIVATE_STATE;
import static tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.PRIVATE_TRANSACTIONS;
import static tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.PRUNING_STATE;
import static tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.WORLD_STATE;

import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.PantheonConfiguration;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageFactory;
import tech.pegasys.pantheon.services.kvstore.LimitedInMemoryKeyValueStorage;

public class KeyValueStorageProviderBuilder {

  private static final long DEFAULT_WORLD_STATE_PRE_IMAGE_CACHE_SIZE = 5_000L;

  private KeyValueStorageFactory storageFactory;
  private PantheonConfiguration commonConfiguration;
  private MetricsSystem metricsSystem;

  public KeyValueStorageProviderBuilder withStorageFactory(
      final KeyValueStorageFactory storageFactory) {
    this.storageFactory = storageFactory;
    return this;
  }

  public KeyValueStorageProviderBuilder withCommonConfiguration(
      final PantheonConfiguration commonConfiguration) {
    this.commonConfiguration = commonConfiguration;
    return this;
  }

  public KeyValueStorageProviderBuilder withMetricsSystem(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public KeyValueStorageProvider build() {
    checkNotNull(storageFactory, "Cannot build a storage provider without a storage factory.");
    checkNotNull(
        commonConfiguration,
        "Cannot build a storage provider without the plugin common configuration.");
    checkNotNull(metricsSystem, "Cannot build a storage provider without a metrics system.");

    final KeyValueStorage worldStatePreImageStorage =
        new LimitedInMemoryKeyValueStorage(DEFAULT_WORLD_STATE_PRE_IMAGE_CACHE_SIZE);

    return new KeyValueStorageProvider(
        storageFactory.create(BLOCKCHAIN, commonConfiguration, metricsSystem),
        storageFactory.create(WORLD_STATE, commonConfiguration, metricsSystem),
        worldStatePreImageStorage,
        storageFactory.create(PRIVATE_TRANSACTIONS, commonConfiguration, metricsSystem),
        storageFactory.create(PRIVATE_STATE, commonConfiguration, metricsSystem),
        storageFactory.create(PRUNING_STATE, commonConfiguration, metricsSystem),
        storageFactory.isSegmentIsolationSupported());
  }
}
