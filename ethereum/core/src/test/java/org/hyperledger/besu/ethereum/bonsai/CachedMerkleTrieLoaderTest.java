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
package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TrieGenerator;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CachedMerkleTrieLoaderTest {

  private CachedMerkleTrieLoader merkleTrieLoader;
  private final StorageProvider storageProvider = new InMemoryKeyValueStorageProvider();
  private final BonsaiWorldStateKeyValueStorage inMemoryWorldState =
      Mockito.spy(new BonsaiWorldStateKeyValueStorage(storageProvider, new NoOpMetricsSystem()));

  final List<Address> accounts =
      List.of(Address.fromHexString("0xdeadbeef"), Address.fromHexString("0xdeadbeee"));

  private MerkleTrie<Bytes, Bytes> trie;

  @BeforeEach
  public void setup() {
    trie =
        TrieGenerator.generateTrie(
            inMemoryWorldState, accounts.stream().map(Hash::hash).collect(Collectors.toList()));
    merkleTrieLoader = new CachedMerkleTrieLoader(new NoOpMetricsSystem());
  }

  @Test
  public void shouldAddAccountNodesInCacheDuringPreload() {
    merkleTrieLoader.cacheAccountNodes(
        inMemoryWorldState, Hash.wrap(trie.getRootHash()), accounts.get(0));

    final BonsaiWorldStateKeyValueStorage emptyStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem());
    StoredMerklePatriciaTrie<Bytes, Bytes> cachedTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                merkleTrieLoader.getAccountStateTrieNode(emptyStorage, location, hash),
            trie.getRootHash(),
            Function.identity(),
            Function.identity());

    final Hash hashAccountZero = Hash.hash(accounts.get(0));
    assertThat(cachedTrie.get(hashAccountZero)).isEqualTo(trie.get(hashAccountZero));
  }

  @Test
  public void shouldAddStorageNodesInCacheDuringPreload() {
    final Hash hashAccountZero = Hash.hash(accounts.get(0));
    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(trie.get(hashAccountZero).orElseThrow()));
    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                inMemoryWorldState.getAccountStorageTrieNode(hashAccountZero, location, hash),
            stateTrieAccountValue.getStorageRoot(),
            Function.identity(),
            Function.identity());
    final List<Bytes> originalSlots = new ArrayList<>();
    storageTrie.visitLeafs(
        (keyHash, node) -> {
          merkleTrieLoader.cacheStorageNodes(
              inMemoryWorldState,
              accounts.get(0),
              new StorageSlotKey(Hash.wrap(keyHash), Optional.empty()));
          originalSlots.add(node.getEncodedBytes());
          return TrieIterator.State.CONTINUE;
        });

    final List<Bytes> cachedSlots = new ArrayList<>();
    final BonsaiWorldStateKeyValueStorage emptyStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(), new NoOpMetricsSystem());
    final StoredMerklePatriciaTrie<Bytes, Bytes> cachedTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                merkleTrieLoader.getAccountStorageTrieNode(
                    emptyStorage, hashAccountZero, location, hash),
            stateTrieAccountValue.getStorageRoot(),
            Function.identity(),
            Function.identity());
    cachedTrie.visitLeafs(
        (keyHash, node) -> {
          cachedSlots.add(node.getEncodedBytes());
          return TrieIterator.State.CONTINUE;
        });
    assertThat(originalSlots).isNotEmpty();
    assertThat(originalSlots).isEqualTo(cachedSlots);
  }

  @Test
  public void shouldFallbackWhenAccountNodesIsNotInCache() {
    final StoredMerklePatriciaTrie<Bytes, Bytes> cachedTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                merkleTrieLoader.getAccountStateTrieNode(inMemoryWorldState, location, hash),
            trie.getRootHash(),
            Function.identity(),
            Function.identity());
    final Hash hashAccountZero = Hash.hash(accounts.get(0));
    assertThat(cachedTrie.get(hashAccountZero)).isEqualTo(trie.get(hashAccountZero));
  }

  @Test
  public void shouldFallbackWhenStorageNodesIsNotInCache() {
    final Hash hashAccountZero = Hash.hash(accounts.get(0));
    final StateTrieAccountValue stateTrieAccountValue =
        StateTrieAccountValue.readFrom(RLP.input(trie.get(hashAccountZero).orElseThrow()));
    final StoredMerklePatriciaTrie<Bytes, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                inMemoryWorldState.getAccountStorageTrieNode(hashAccountZero, location, hash),
            stateTrieAccountValue.getStorageRoot(),
            Function.identity(),
            Function.identity());
    final List<Bytes> originalSlots = new ArrayList<>();
    storageTrie.visitLeafs(
        (keyHash, node) -> {
          originalSlots.add(node.getEncodedBytes());
          return TrieIterator.State.CONTINUE;
        });

    final List<Bytes> cachedSlots = new ArrayList<>();
    final StoredMerklePatriciaTrie<Bytes, Bytes> cachedTrie =
        new StoredMerklePatriciaTrie<>(
            (location, hash) ->
                merkleTrieLoader.getAccountStorageTrieNode(
                    inMemoryWorldState, hashAccountZero, location, hash),
            stateTrieAccountValue.getStorageRoot(),
            Function.identity(),
            Function.identity());
    cachedTrie.visitLeafs(
        (keyHash, node) -> {
          cachedSlots.add(node.getEncodedBytes());
          return TrieIterator.State.CONTINUE;
        });
    assertThat(originalSlots).isNotEmpty();
    assertThat(originalSlots).isEqualTo(cachedSlots);
  }
}
