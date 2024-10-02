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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.cache.preloader.TrieNodePreLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;
import org.hyperledger.besu.ethereum.verkletrie.VerkleTrie;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TrieNodePreloaderTests {

  private static final PedersenHasher PEDERSEN_HASHER = new PedersenHasher();
  private static final TrieKeyAdapter TRIE_KEY_ADAPTER = new TrieKeyAdapter();
  private TrieNodePreLoader trieNodePreLoader;

  private VerkleWorldStateKeyValueStorage worldStateKeyValueStorage;

  @BeforeEach
  public void setup() {
    this.worldStateKeyValueStorage =
        new VerkleWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem());
    this.trieNodePreLoader = new TrieNodePreLoader(worldStateKeyValueStorage);
  }

  @Test
  public void trieNodeLoaderFindsNodesUsingCache() {
    final VerkleTrie stateTrie = new VerkleTrie((location, hash) -> Optional.empty());
    final Bytes stem = PEDERSEN_HASHER.computeStem(Address.ZERO, UInt256.ZERO);
    final Bytes trieKey = TRIE_KEY_ADAPTER.versionKey(Address.ZERO);
    stateTrie.put(trieKey, Bytes.of(0x01));

    final VerkleWorldStateKeyValueStorage.Updater updater = worldStateKeyValueStorage.updater();
    stateTrie.commit((location, hash, value) -> updater.putStateTrieNode(location, value));
    updater.commit();

    trieNodePreLoader.cacheNodes(stem);

    worldStateKeyValueStorage.clear(); // clear the storage to be sure we are reading from the cache

    final VerkleTrie cachedStateTrie =
        new VerkleTrie((location, hash) -> trieNodePreLoader.getStateTrieNode(location));
    Assertions.assertThat(cachedStateTrie.get(trieKey)).contains(Bytes32.leftPad(Bytes.of(0x01)));
  }

  @Test
  public void trieNodeLoaderShouldFallbackToStorageWhenCacheIsEmpty() {
    final VerkleTrie stateTrie = new VerkleTrie((location, hash) -> Optional.empty());
    final Bytes trieKey = TRIE_KEY_ADAPTER.versionKey(Address.ZERO);
    stateTrie.put(trieKey, Bytes.of(0x01));

    final VerkleWorldStateKeyValueStorage.Updater updater = worldStateKeyValueStorage.updater();
    stateTrie.commit((location, hash, value) -> updater.putStateTrieNode(location, value));
    updater.commit();

    final VerkleTrie cachedStateTrie =
        new VerkleTrie((location, hash) -> trieNodePreLoader.getStateTrieNode(location));
    Assertions.assertThat(cachedStateTrie.get(trieKey)).contains(Bytes32.leftPad(Bytes.of(0x01)));
  }
}
