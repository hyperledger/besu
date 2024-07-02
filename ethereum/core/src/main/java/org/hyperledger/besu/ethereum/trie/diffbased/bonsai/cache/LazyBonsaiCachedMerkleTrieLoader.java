/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.prometheus.client.guava.cache.CacheMetricsCollector;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.StorageSubscriber;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class LazyBonsaiCachedMerkleTrieLoader extends BonsaiCachedMerkleTrieLoader{


  private final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader;

  public LazyBonsaiCachedMerkleTrieLoader(final ObservableMetricsSystem metricsSystem, final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader) {
    super(metricsSystem);
    this.bonsaiCachedMerkleTrieLoader = bonsaiCachedMerkleTrieLoader;
  }

  public void preLoadAccount(
          final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
          final Hash worldStateRootHash,
          final Address account) {
   
  }

  public void preLoadStorageSlot(
          final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
          final Address account,
          final StorageSlotKey slotKey) {
      }
}
