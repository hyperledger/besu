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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.preload;

import static org.hyperledger.besu.metrics.BesuMetricCategory.BLOCKCHAIN;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.Consumer;
import org.hyperledger.besu.ethereum.trie.diffbased.common.preload.PreloaderManager;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * BonsaiMerkleTriePreloader is responsible for preloading trie nodes for Bonsai accounts and
 * storage slots. This preloader improves performance by preloading and caching frequently trie
 * nodes of the touched data.
 */
public class BonsaiMerkleTriePreloader extends PreloaderManager<BonsaiAccount>
    implements StorageSubscriber {

  // Maximum cache sizes for accounts and storage nodes.
  private static final int ACCOUNT_CACHE_SIZE = 100_000;
  private static final int STORAGE_CACHE_SIZE = 200_000;

  private final Cache<Bytes, Bytes> accountNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(ACCOUNT_CACHE_SIZE).build();
  private final Cache<Bytes, Bytes> storageNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(STORAGE_CACHE_SIZE).build();

  /**
   * Constructor that initializes the preloader and registers cache metrics with the metrics system.
   *
   * @param metricsSystem the metrics used to record cache statistics.
   */
  public BonsaiMerkleTriePreloader(final ObservableMetricsSystem metricsSystem) {
    metricsSystem.createGuavaCacheCollector(BLOCKCHAIN, "accountsNodes", accountNodes);
    metricsSystem.createGuavaCacheCollector(BLOCKCHAIN, "storageNodes", storageNodes);
  }

  /**
   * Preloads and caches account trie nodes for a specific account.
   *
   * @param worldStateKeyValueStorage the key-value storage interface for the world state.
   * @param worldStateRootHash the root hash of the world state trie.
   * @param account the account whose trie nodes need to be cached.
   */
  @VisibleForTesting
  public void cacheAccountNodes(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash worldStateRootHash,
      final Address account) {
    final long storageSubscriberId = worldStateKeyValueStorage.subscribe(this);
    try {
      final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              (location, hash) -> {
                Optional<Bytes> node =
                    getAccountStateTrieNode(worldStateKeyValueStorage, location, hash);
                node.ifPresent(bytes -> accountNodes.put(Hash.hash(bytes), bytes));
                return node;
              },
              worldStateRootHash,
              Function.identity(),
              Function.identity());
      accountTrie.get(account.addressHash());
    } catch (MerkleTrieException e) {
      // ignore exception for the cache
    } finally {
      worldStateKeyValueStorage.unSubscribe(storageSubscriberId);
    }
  }

  /**
   * Preloads and caches storage trie nodes for a specific account and storage slot.
   *
   * @param worldStateKeyValueStorage the key-value storage interface for the world state.
   * @param account the account for which storage nodes are to be cached.
   * @param slotKey the specific storage slot key to preload.
   */
  @VisibleForTesting
  public void cacheStorageNodes(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Address account,
      final StorageSlotKey slotKey) {
    final Hash accountHash = account.addressHash();
    final long storageSubscriberId = worldStateKeyValueStorage.subscribe(this);
    try {
      worldStateKeyValueStorage
          .getStateTrieNode(Bytes.concatenate(accountHash, Bytes.EMPTY))
          .ifPresent(
              storageRoot -> {
                try {
                  final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
                      new StoredMerklePatriciaTrie<>(
                          (location, hash) -> {
                            Optional<Bytes> node =
                                getAccountStorageTrieNode(
                                    worldStateKeyValueStorage, accountHash, location, hash);
                            node.ifPresent(bytes -> storageNodes.put(Hash.hash(bytes), bytes));
                            return node;
                          },
                          Hash.hash(storageRoot),
                          Function.identity(),
                          Function.identity());
                  storageTrie.get(slotKey.getSlotHash());
                } catch (MerkleTrieException e) {
                  // ignore exception for the cache
                }
              });
    } finally {
      worldStateKeyValueStorage.unSubscribe(storageSubscriberId);
    }
  }

  /**
   * Retrieves an account state trie node from the cache or storage.
   *
   * @param worldStateKeyValueStorage the world state key-value storage interface.
   * @param location the location of the node within the trie.
   * @param nodeHash the hash of the node to retrieve.
   * @return an Optional containing the node bytes if found.
   */
  public Optional<Bytes> getAccountStateTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Bytes location,
      final Bytes32 nodeHash) {
    // If the node hash represents an empty trie node, return the known empty node.
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      // Otherwise, try to retrieve the node from the cache; if absent, load it from storage.
      return Optional.ofNullable(accountNodes.getIfPresent(nodeHash))
          .or(() -> worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash));
    }
  }

  /**
   * Retrieves a storage trie node from the cache or storage.
   *
   * @param worldStateKeyValueStorage the world state key-value storage interface.
   * @param accountHash the hash of the account.
   * @param location the location of the node within the storage trie.
   * @param nodeHash the hash of the node to retrieve.
   * @return an Optional containing the node bytes if found.
   */
  public Optional<Bytes> getAccountStorageTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash) {
    // If the node hash represents an empty trie node, return the known empty node.
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      // Otherwise, try to retrieve the node from the storage cache; if absent, load it from
      // storage.
      return Optional.ofNullable(storageNodes.getIfPresent(nodeHash))
          .or(
              () ->
                  worldStateKeyValueStorage.getAccountStorageTrieNode(
                      accountHash, location, nodeHash));
    }
  }

  /**
   * Provides a consumer to asynchronously preload account trie nodes.
   *
   * <p>This method returns a Consumer that, when invoked, will asynchronously run the
   * cacheAccountNodes method using the provided world state.
   *
   * @param worldState the diff-based world state.
   * @return a Consumer that preloads account data.
   */
  @Override
  public Consumer<DiffBasedValue<BonsaiAccount>> getAccountPreloader(
      final DiffBasedWorldState worldState) {
    return (address, value) ->
        CompletableFuture.runAsync(
            () ->
                cacheAccountNodes(
                    (BonsaiWorldStateKeyValueStorage) worldState.getWorldStateStorage(),
                    worldState.getWorldStateRootHash(),
                    address));
  }

  /**
   * Provides a consumer to asynchronously preload storage trie nodes.
   *
   * <p>This method returns a Consumer that, when invoked, will asynchronously run the
   * cacheStorageNodes method using the provided world state.
   *
   * @param worldState the diff-based world state.
   * @return a Consumer that preloads storage slot data.
   */
  @Override
  public Consumer<StorageSlotKey> getStoragePreloader(final DiffBasedWorldState worldState) {
    return (address, slotKey) ->
        CompletableFuture.runAsync(
            () ->
                cacheStorageNodes(
                    (BonsaiWorldStateKeyValueStorage) worldState.getWorldStateStorage(),
                    address,
                    slotKey));
  }
}
