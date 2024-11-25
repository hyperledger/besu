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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldStateConfig;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.ServiceManager;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiWorldStateProvider extends DiffBasedWorldStateProvider {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiWorldStateProvider.class);
  private final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader;
  private final Supplier<WorldStateHealer> worldStateHealerSupplier;

  public BonsaiWorldStateProvider(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final ServiceManager pluginContext,
      final EvmConfiguration evmConfiguration,
      final Supplier<WorldStateHealer> worldStateHealerSupplier) {
    super(worldStateKeyValueStorage, blockchain, maxLayersToLoad, pluginContext);
    this.bonsaiCachedMerkleTrieLoader = bonsaiCachedMerkleTrieLoader;
    this.worldStateHealerSupplier = worldStateHealerSupplier;
    provideCachedWorldStorageManager(
        new BonsaiCachedWorldStorageManager(
            this, worldStateKeyValueStorage, this::cloneBonsaiWorldStateConfig));
    loadPersistedState(
        new BonsaiWorldState(
            this, worldStateKeyValueStorage, evmConfiguration, defaultWorldStateConfig));
  }

  @VisibleForTesting
  BonsaiWorldStateProvider(
      final BonsaiCachedWorldStorageManager bonsaiCachedWorldStorageManager,
      final TrieLogManager trieLogManager,
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final EvmConfiguration evmConfiguration,
      final Supplier<WorldStateHealer> worldStateHealerSupplier) {
    super(worldStateKeyValueStorage, blockchain, trieLogManager);
    this.bonsaiCachedMerkleTrieLoader = bonsaiCachedMerkleTrieLoader;
    this.worldStateHealerSupplier = worldStateHealerSupplier;
    provideCachedWorldStorageManager(bonsaiCachedWorldStorageManager);
    loadPersistedState(
        new BonsaiWorldState(
            this, worldStateKeyValueStorage, evmConfiguration, defaultWorldStateConfig));
  }

  public BonsaiCachedMerkleTrieLoader getCachedMerkleTrieLoader() {
    return bonsaiCachedMerkleTrieLoader;
  }

  private BonsaiWorldStateKeyValueStorage getBonsaiWorldStateKeyValueStorage() {
    return (BonsaiWorldStateKeyValueStorage) worldStateKeyValueStorage;
  }

  /**
   * Prepares the state healing process for a given address and location. It prepares the state
   * healing, including retrieving data from storage, identifying invalid slots or nodes, removing
   * account and slot from the state trie, and committing the changes. Finally, it downgrades the
   * world state storage to partial flat database mode.
   */
  public void prepareStateHealing(final Address address, final Bytes location) {
    final Set<Bytes> keysToDelete = new HashSet<>();
    final BonsaiWorldStateKeyValueStorage.Updater updater =
        getBonsaiWorldStateKeyValueStorage().updater();
    final Hash accountHash = address.addressHash();
    final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
        new StoredMerklePatriciaTrie<>(
            (l, h) -> {
              final Optional<Bytes> node =
                  getBonsaiWorldStateKeyValueStorage().getAccountStateTrieNode(l, h);
              if (node.isPresent()) {
                keysToDelete.add(l);
              }
              return node;
            },
            persistedState.getWorldStateRootHash(),
            Function.identity(),
            Function.identity());
    try {
      accountTrie
          .get(accountHash)
          .map(RLP::input)
          .map(PmtStateTrieAccountValue::readFrom)
          .ifPresent(
              account -> {
                final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
                    new StoredMerklePatriciaTrie<>(
                        (l, h) -> {
                          Optional<Bytes> node =
                              getBonsaiWorldStateKeyValueStorage()
                                  .getAccountStorageTrieNode(accountHash, l, h);
                          if (node.isPresent()) {
                            keysToDelete.add(Bytes.concatenate(accountHash, l));
                          }
                          return node;
                        },
                        account.getStorageRoot(),
                        Function.identity(),
                        Function.identity());
                try {
                  storageTrie.getPath(location);
                } catch (Exception eA) {
                  LOG.warn("Invalid slot found for account {} at location {}", address, location);
                  // ignore
                }
              });
    } catch (Exception eA) {
      LOG.warn("Invalid node for account {} at location {}", address, location);
      // ignore
    }
    keysToDelete.forEach(updater::removeAccountStateTrieNode);
    updater.commit();

    getBonsaiWorldStateKeyValueStorage().downgradeToPartialFlatDbMode();
  }

  private DiffBasedWorldStateConfig cloneBonsaiWorldStateConfig() {
    return new DiffBasedWorldStateConfig(defaultWorldStateConfig);
  }

  @Override
  public void heal(final Optional<Address> maybeAccountToRepair, final Bytes location) {
    worldStateHealerSupplier.get().heal(maybeAccountToRepair, location);
  }
}
