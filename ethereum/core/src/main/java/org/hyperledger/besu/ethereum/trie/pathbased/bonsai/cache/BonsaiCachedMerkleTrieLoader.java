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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache;

import static org.hyperledger.besu.metrics.BesuMetricCategory.BLOCKCHAIN;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiCachedMerkleTrieLoader implements StorageSubscriber {

  private static final int ACCOUNT_CACHE_SIZE = 100_000;
  private static final int STORAGE_CACHE_SIZE = 200_000;

  private final Cache<Bytes, Bytes> accountNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(ACCOUNT_CACHE_SIZE).build();
  private final Cache<Bytes, Bytes> storageNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(STORAGE_CACHE_SIZE).build();

  private final OperationTimer accountPreloadTimer;
  private final OperationTimer storagePreloadTimer;

  private final Counter accountCacheMissCounter;
  private final Counter storageCacheMissCounter;

  public BonsaiCachedMerkleTrieLoader(final ObservableMetricsSystem metricsSystem) {
    metricsSystem.createGuavaCacheCollector(BLOCKCHAIN, "accountsNodes", accountNodes);
    metricsSystem.createGuavaCacheCollector(BLOCKCHAIN, "storageNodes", storageNodes);

    accountPreloadTimer =
        metricsSystem
            .createLabelledTimer(
                BLOCKCHAIN,
                "account_preload_latency_seconds",
                "Latency for preloading account nodes",
                "database")
            .labels("besu");
    storagePreloadTimer =
        metricsSystem
            .createLabelledTimer(
                BLOCKCHAIN,
                "storage_preload_latency_seconds",
                "Latency for preloading storage nodes",
                "database")
            .labels("besu");

    accountCacheMissCounter =
        metricsSystem.createCounter(
            BLOCKCHAIN, "account_cache_miss_count", "Counter for account state trie cache misses");
    storageCacheMissCounter =
        metricsSystem.createCounter(
            BLOCKCHAIN,
            "storage_cache_miss_count",
            "Counter for account storage trie cache misses");
  }

  public void preLoadAccount(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash worldStateRootHash,
      final Address account) {
    CompletableFuture.runAsync(
        () -> cacheAccountNodes(worldStateKeyValueStorage, worldStateRootHash, account));
  }

  @VisibleForTesting
  public void cacheAccountNodes(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash worldStateRootHash,
      final Address account) {
    final OperationTimer.TimingContext timingContext = accountPreloadTimer.startTimer();
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
    } finally {
      worldStateKeyValueStorage.unSubscribe(storageSubscriberId);
      timingContext.close();
    }
  }

  public void preLoadStorageSlot(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Address account,
      final StorageSlotKey slotKey) {
    CompletableFuture.runAsync(
        () -> cacheStorageNodes(worldStateKeyValueStorage, account, slotKey));
  }

  @VisibleForTesting
  public void cacheStorageNodes(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Address account,
      final StorageSlotKey slotKey) {
    final OperationTimer.TimingContext timingContext = storagePreloadTimer.startTimer();
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
      timingContext.close();
    }
  }

  public Optional<Bytes> getAccountStateTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Bytes location,
      final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return Optional.ofNullable(accountNodes.getIfPresent(nodeHash))
          .or(
              () -> {
                accountCacheMissCounter.inc();
                return worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash);
              });
    }
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return Optional.ofNullable(storageNodes.getIfPresent(nodeHash))
          .or(
              () -> {
                storageCacheMissCounter.inc();
                return worldStateKeyValueStorage.getAccountStorageTrieNode(
                    accountHash, location, nodeHash);
              });
    }
  }
}
