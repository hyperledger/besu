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
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.prometheus.client.guava.cache.CacheMetricsCollector;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class OptimizedMerkleTrieLoader {

  private static final int ACCOUNT_CACHE_SIZE = 100_000;
  private static final int STORAGE_CACHE_SIZE = 200_000;
  private final Cache<Bytes, Bytes> accountsNodes;
  private final Cache<Bytes, Bytes> storageNodes;

  public OptimizedMerkleTrieLoader(final ObservableMetricsSystem metricsSystem) {
    accountsNodes = CacheBuilder.newBuilder().recordStats().maximumSize(ACCOUNT_CACHE_SIZE).build();
    storageNodes = CacheBuilder.newBuilder().recordStats().maximumSize(STORAGE_CACHE_SIZE).build();

    CacheMetricsCollector cacheMetrics = new CacheMetricsCollector();
    cacheMetrics.addCache("accountsNodes", accountsNodes);
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
        () -> {
          final StoredMerklePatriciaTrie<Bytes, Bytes> accountTrie =
              new StoredMerklePatriciaTrie<>(
                  (location, hash) -> {
                    Optional<Bytes> node =
                        worldStateStorage.getAccountStateTrieNode(location, hash);
                    node.ifPresent(bytes -> accountsNodes.put(Hash.hash(bytes), bytes));
                    return node;
                  },
                  worldStateRootHash,
                  Function.identity(),
                  Function.identity());
          accountTrie.get(Hash.hash(account));
        });
  }

  public void preLoadStorage(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Address account,
      final Hash slotHash) {
    CompletableFuture.runAsync(
        () -> {
          final Hash accountHash = Hash.hash(account);
          worldStateStorage
              .getStateTrieNode(Bytes.concatenate(accountHash, Bytes.EMPTY))
              .ifPresent(
                  storageRoot -> {
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
                  });
        });
  }

  public Optional<Bytes> getAccountStateTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Bytes location,
      final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      Optional<Bytes> node = Optional.ofNullable(accountsNodes.getIfPresent(nodeHash));
      if (node.isPresent()) {
        return node;
      } else {
        return worldStateKeyValueStorage.getAccountStateTrieNode(location, nodeHash);
      }
    }
  }

  public Optional<Bytes> getAccountStorageTrieNode(
      final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Hash accountHash,
      final Bytes location,
      final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      Optional<Bytes> node = Optional.ofNullable(storageNodes.getIfPresent(nodeHash));
      if (node.isPresent()) {
        return node;
      } else {
        return worldStateKeyValueStorage.getAccountStorageTrieNode(accountHash, location, nodeHash);
      }
    }
  }
}
