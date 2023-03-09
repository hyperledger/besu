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
package org.hyperledger.besu.ethereum.trie;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;

public class StoredMerklePatriciaTrieTest extends AbstractMerklePatriciaTrieTest {
  private KeyValueStorage keyValueStore;
  private MerkleStorage merkleStorage;
  private Function<String, Bytes> valueSerializer;
  private Function<Bytes, String> valueDeserializer;

  @Override
  protected MerklePatriciaTrie<Bytes, String> createTrie() {
    keyValueStore = new InMemoryKeyValueStorage();
    merkleStorage = new KeyValueMerkleStorage(keyValueStore);
    valueSerializer =
        value -> (value != null) ? Bytes.wrap(value.getBytes(StandardCharsets.UTF_8)) : null;
    valueDeserializer = bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8);
    return new StoredMerklePatriciaTrie<>(merkleStorage::get, valueSerializer, valueDeserializer);
  }

  @Test
  public void canReloadTrieFromHash() {
    final Bytes key1 = Bytes.of(1, 5, 8, 9);
    final Bytes key2 = Bytes.of(1, 6, 1, 2);
    final Bytes key3 = Bytes.of(1, 6, 1, 3);

    // Push some values into the trie and commit changes so nodes are persisted
    final String value1 = "value1";
    trie.put(key1, value1);
    final Bytes32 hash1 = trie.getRootHash();
    trie.commit(merkleStorage::put);

    final String value2 = "value2";
    trie.put(key2, value2);
    final String value3 = "value3";
    trie.put(key3, value3);
    final Bytes32 hash2 = trie.getRootHash();
    trie.commit(merkleStorage::put);

    final String value4 = "value4";
    trie.put(key1, value4);
    final Bytes32 hash3 = trie.getRootHash();
    trie.commit(merkleStorage::put);

    // Check the root hashes for 3 tries are all distinct
    assertThat(hash1).isNotEqualTo(hash2);
    assertThat(hash1).isNotEqualTo(hash3);
    assertThat(hash2).isNotEqualTo(hash3);
    // And that we can retrieve the last value we set for key1
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value4"));

    // Create new tries from root hashes and check that we find expected values
    trie =
        new StoredMerklePatriciaTrie<>(
            merkleStorage::get, hash1, valueSerializer, valueDeserializer);
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value1"));
    assertThat(trie.get(key2)).isEqualTo(Optional.empty());
    assertThat(trie.get(key3)).isEqualTo(Optional.empty());

    trie =
        new StoredMerklePatriciaTrie<>(
            merkleStorage::get, hash2, valueSerializer, valueDeserializer);
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value1"));
    assertThat(trie.get(key2)).isEqualTo(Optional.of("value2"));
    assertThat(trie.get(key3)).isEqualTo(Optional.of("value3"));

    trie =
        new StoredMerklePatriciaTrie<>(
            merkleStorage::get, hash3, valueSerializer, valueDeserializer);
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value4"));
    assertThat(trie.get(key2)).isEqualTo(Optional.of("value2"));
    assertThat(trie.get(key3)).isEqualTo(Optional.of("value3"));

    // Commit changes to storage, and create new tries from roothash and new storage instance
    merkleStorage.commit();
    final MerkleStorage newMerkleStorage = new KeyValueMerkleStorage(keyValueStore);
    trie =
        new StoredMerklePatriciaTrie<>(
            newMerkleStorage::get, hash1, valueSerializer, valueDeserializer);
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value1"));
    assertThat(trie.get(key2)).isEqualTo(Optional.empty());
    assertThat(trie.get(key3)).isEqualTo(Optional.empty());

    trie =
        new StoredMerklePatriciaTrie<>(
            newMerkleStorage::get, hash2, valueSerializer, valueDeserializer);
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value1"));
    assertThat(trie.get(key2)).isEqualTo(Optional.of("value2"));
    assertThat(trie.get(key3)).isEqualTo(Optional.of("value3"));

    trie =
        new StoredMerklePatriciaTrie<>(
            newMerkleStorage::get, hash3, valueSerializer, valueDeserializer);
    assertThat(trie.get(key1)).isEqualTo(Optional.of("value4"));
    assertThat(trie.get(key2)).isEqualTo(Optional.of("value2"));
    assertThat(trie.get(key3)).isEqualTo(Optional.of("value3"));
  }
}
