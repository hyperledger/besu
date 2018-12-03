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
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.db.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;

public class InMemoryStorageProvider implements StorageProvider {

  public static MutableBlockchain createInMemoryBlockchain(final Block genesisBlock) {
    return createInMemoryBlockchain(genesisBlock, MainnetBlockHashFunction::createHash);
  }

  public static MutableBlockchain createInMemoryBlockchain(
      final Block genesisBlock, final BlockHashFunction blockHashFunction) {
    final InMemoryKeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    return new DefaultMutableBlockchain(
        genesisBlock,
        new KeyValueStoragePrefixedKeyBlockchainStorage(keyValueStorage, blockHashFunction),
        new NoOpMetricsSystem());
  }

  public static WorldStateArchive createInMemoryWorldStateArchive() {
    return new WorldStateArchive(
        new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage()));
  }

  @Override
  public BlockchainStorage createBlockchainStorage(final ProtocolSchedule<?> protocolSchedule) {
    return new KeyValueStoragePrefixedKeyBlockchainStorage(
        new InMemoryKeyValueStorage(), ScheduleBasedBlockHashFunction.create(protocolSchedule));
  }

  @Override
  public WorldStateStorage createWorldStateStorage() {
    return new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage());
  }

  @Override
  public void close() {}
}
