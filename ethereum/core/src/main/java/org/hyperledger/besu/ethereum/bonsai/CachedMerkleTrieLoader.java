/*
 * Copyright Hyperledger Besu contributors.
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
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.prometheus.client.guava.cache.CacheMetricsCollector;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class CachedMerkleTrieLoader implements BonsaiStorageSubscriber {

  private static final int ACCOUNT_CACHE_SIZE = 100_000;
  private static final int STORAGE_CACHE_SIZE = 200_000;
  private final Cache<Bytes, Bytes> accountNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(ACCOUNT_CACHE_SIZE).build();
  private final Cache<Bytes, Bytes> storageNodes =
      CacheBuilder.newBuilder().recordStats().maximumSize(STORAGE_CACHE_SIZE).build();

  public CachedMerkleTrieLoader(final ObservableMetricsSystem metricsSystem) {

    CacheMetricsCollector cacheMetrics = new CacheMetricsCollector();
    cacheMetrics.addCache("accountsNodes", accountNodes);
    cacheMetrics.addCache("storageNodes", storageNodes);
    if (metricsSystem instanceof PrometheusMetricsSystem)
      ((PrometheusMetricsSystem) metricsSystem)
          .addCollector(BesuMetricCategory.BLOCKCHAIN, () -> cacheMetrics);
  }

  public void preLoadAccount(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Hash worldStateRootHash,
      final Address account) {
    CompletableFuture.runAsync(
        () -> cacheAccountNodes(worldStateStorage, worldStateRootHash, account));
  }

  @VisibleForTesting
  public void cacheAccountNodes(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Hash worldStateRootHash,
      final Address account) {
    final long storageSubscriberId = worldStateStorage.subscribe(this);
    try {
      final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              (location, hash) -> {
                Optional<Bytes> node = worldStateStorage.getAccountStateTrieNode(location, hash);
                ;
                node.ifPresent(bytes -> accountNodes.put(Hash.hash(bytes), bytes));
                return node;
              },
              worldStateRootHash,
              Function.identity(),
              Function.identity());
      accountTrie.get(Hash.hash(account));
    } catch (MerkleTrieException e) {
      // ignore exception for the cache
    } finally {
      worldStateStorage.unSubscribe(storageSubscriberId);
    }
  }

  public void preLoadStorageSlot(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Address account,
      final Hash slotHash) {
    CompletableFuture.runAsync(() -> cacheStorageNodes(worldStateStorage, account, slotHash));
  }

  public void preLoadAccountTrie(final StoredMerklePatriciaTrie trie) {
    CompletableFuture.runAsync(() ->
            trie.commit(
                    (location, hash, value) ->
                            this.cacheAccountStateTrieNode(hash, value)));
  }

  public void preLoadStorageTrie(final StoredMerklePatriciaTrie trie) {
    CompletableFuture.runAsync(() ->
            trie.commit(
                    (location, hash, value) ->
                            this.cacheAccountStorageTrieNode(hash, value)));
  }

  @VisibleForTesting
  public void cacheStorageNodes(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Address account,
      final Hash slotHash) {
    final Hash accountHash = Hash.hash(account);
    final long storageSubscriberId = worldStateStorage.subscribe(this);
    try {
      worldStateStorage
          .getStateTrieNode(Bytes.concatenate(accountHash, Bytes.EMPTY))
          .ifPresent(
              storageRoot -> {
                try {
                  final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
                      new StoredMerklePatriciaTrie<>(
                          (location, hash) -> {
                            Optional<Bytes> node =
                                worldStateStorage.getAccountStorageTrieNode(
                                    accountHash, location, hash);
                            node.ifPresent(bytes -> storageNodes.put(Hash.hash(bytes), bytes));
                            return node;
                          },
                          Hash.hash(storageRoot),
                          Function.identity(),
                          Function.identity());
                  storageTrie.get(slotHash);
                } catch (MerkleTrieException e) {
                  // ignore exception for the cache
                }
              });
    } finally {
      worldStateStorage.unSubscribe(storageSubscriberId);
    }
  }

  public Optional<Bytes> getAccountStateTrieNode(final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return Optional.ofNullable(accountNodes.getIfPresent(nodeHash));
    }
  }

  public void cacheAccountStateTrieNode(final Bytes32 nodeHash, final Bytes value) {
    if (!nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      accountNodes.put(nodeHash, value);
    }
  }

  public Optional<Bytes> getAccountStorageTrieNode(final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return Optional.ofNullable(storageNodes.getIfPresent(nodeHash));
    }
  }

  public void cacheAccountStorageTrieNode(final Bytes32 nodeHash, final Bytes value) {
    if (!nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      storageNodes.put(nodeHash, value);
    }
  }
}
