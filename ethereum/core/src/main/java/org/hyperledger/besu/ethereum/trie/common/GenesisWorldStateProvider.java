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

package org.hyperledger.besu.ethereum.trie.common;

import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.bonsai.cache.NoOpCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.NoOpTrieLogManager;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.forest.worldview.ForestMutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.Objects;

public class GenesisWorldStateProvider {

  /**
   * Creates a Genesis world state based on the provided data storage format.
   *
   * @param dataStorageConfiguration the data storage configuration to use
   * @return a mutable world state for the Genesis block
   */
  public static MutableWorldState createGenesisWorldState(
      final DataStorageConfiguration dataStorageConfiguration) {
    if (Objects.requireNonNull(dataStorageConfiguration).getDataStorageFormat()
        == DataStorageFormat.BONSAI) {
      return createGenesisBonsaiWorldState();
    } else {
      return createGenesisForestWorldState();
    }
  }

  /**
   * Creates a Genesis world state using the Bonsai data storage format.
   *
   * @return a mutable world state for the Genesis block
   */
  private static MutableWorldState createGenesisBonsaiWorldState() {
    final CachedMerkleTrieLoader cachedMerkleTrieLoader =
        new CachedMerkleTrieLoader(new NoOpMetricsSystem());
    final BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            new KeyValueStorageProvider(
                segmentIdentifiers -> new SegmentedInMemoryKeyValueStorage(),
                new InMemoryKeyValueStorage(),
                new NoOpMetricsSystem()),
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
    return new BonsaiWorldState(
        bonsaiWorldStateKeyValueStorage,
        cachedMerkleTrieLoader,
        new NoOpCachedWorldStorageManager(bonsaiWorldStateKeyValueStorage),
        new NoOpTrieLogManager(),
        EvmConfiguration.DEFAULT);
  }

  /**
   * Creates a Genesis world state using the Forest data storage format.
   *
   * @return a mutable world state for the Genesis block
   */
  private static MutableWorldState createGenesisForestWorldState() {
    final ForestWorldStateKeyValueStorage stateStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStatePreimageKeyValueStorage preimageStorage =
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage());
    return new ForestMutableWorldState(stateStorage, preimageStorage, EvmConfiguration.DEFAULT);
  }
}
