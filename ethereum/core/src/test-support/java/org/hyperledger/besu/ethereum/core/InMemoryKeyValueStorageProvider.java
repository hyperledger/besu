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
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.TrieLogManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

public class InMemoryKeyValueStorageProvider extends KeyValueStorageProvider {

  public InMemoryKeyValueStorageProvider() {
    super(
        segmentIdentifier -> new InMemoryKeyValueStorage(),
        new InMemoryKeyValueStorage(),
        new InMemoryKeyValueStorage(),
        true);
  }

  public static MutableBlockchain createInMemoryBlockchain(final Block genesisBlock) {
    return createInMemoryBlockchain(genesisBlock, new MainnetBlockHeaderFunctions());
  }

  public static MutableBlockchain createInMemoryBlockchain(
      final Block genesisBlock, final BlockHeaderFunctions blockHeaderFunctions) {
    final InMemoryKeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();
    return DefaultBlockchain.createMutable(
        genesisBlock,
        new KeyValueStoragePrefixedKeyBlockchainStorage(keyValueStorage, blockHeaderFunctions),
        new NoOpMetricsSystem(),
        0);
  }

  public static DefaultWorldStateArchive createInMemoryWorldStateArchive() {
    return new DefaultWorldStateArchive(
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  }

  public static BonsaiWorldStateArchive createBonsaiInMemoryWorldStateArchive(
      final Blockchain blockchain) {
    final InMemoryKeyValueStorageProvider inMemoryKeyValueStorageProvider =
        new InMemoryKeyValueStorageProvider();
    return new BonsaiWorldStateArchive(
        new TrieLogManager(
            blockchain, new BonsaiWorldStateKeyValueStorage(inMemoryKeyValueStorageProvider)),
        inMemoryKeyValueStorageProvider,
        blockchain);
  }

  public static MutableWorldState createInMemoryWorldState() {
    final InMemoryKeyValueStorageProvider provider = new InMemoryKeyValueStorageProvider();
    return new DefaultMutableWorldState(
        provider.createWorldStateStorage(DataStorageFormat.FOREST),
        provider.createWorldStatePreimageStorage());
  }

  public static PrivateStateStorage createInMemoryPrivateStateStorage() {
    return new PrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
  }
}
