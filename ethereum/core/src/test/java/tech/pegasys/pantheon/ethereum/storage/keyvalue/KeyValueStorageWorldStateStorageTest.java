/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;

public class KeyValueStorageWorldStateStorageTest {

  @Test
  public void getCode_returnsEmpty() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    assertThat(storage.getCode(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getAccountStateTrieNode_returnsEmptyNode() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    assertThat(storage.getAccountStateTrieNode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getAccountStorageTrieNode_returnsEmptyNode() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    assertThat(storage.getAccountStorageTrieNode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getNodeData_returnsEmptyValue() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    assertThat(storage.getNodeData(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getNodeData_returnsEmptyNode() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    assertThat(storage.getNodeData(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getCode_saveAndGetSpecialValues() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage
        .updater()
        .putCode(MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putCode(BytesValue.EMPTY)
        .commit();

    assertThat(storage.getCode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getCode(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getCode_saveAndGetRegularValue() {
    BytesValue bytes = BytesValue.fromHexString("0x123456");
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage.updater().putCode(bytes).commit();

    assertThat(storage.getCode(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetSpecialValues() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStateTrieNode(
            Hash.hash(MerklePatriciaTrie.EMPTY_TRIE_NODE), MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putAccountStateTrieNode(Hash.hash(BytesValue.EMPTY), BytesValue.EMPTY)
        .commit();

    assertThat(storage.getAccountStateTrieNode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStateTrieNode(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetRegularValue() {
    BytesValue bytes = BytesValue.fromHexString("0x123456");
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage.updater().putAccountStateTrieNode(Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetSpecialValues() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStorageTrieNode(
            Hash.hash(MerklePatriciaTrie.EMPTY_TRIE_NODE), MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putAccountStorageTrieNode(Hash.hash(BytesValue.EMPTY), BytesValue.EMPTY)
        .commit();

    assertThat(storage.getAccountStorageTrieNode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStorageTrieNode(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetRegularValue() {
    BytesValue bytes = BytesValue.fromHexString("0x123456");
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage.updater().putAccountStorageTrieNode(Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getNodeData_saveAndGetSpecialValues() {
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStorageTrieNode(
            Hash.hash(MerklePatriciaTrie.EMPTY_TRIE_NODE), MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putAccountStorageTrieNode(Hash.hash(BytesValue.EMPTY), BytesValue.EMPTY)
        .commit();

    assertThat(storage.getNodeData(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getNodeData(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getNodeData_saveAndGetRegularValue() {
    BytesValue bytes = BytesValue.fromHexString("0x123456");
    KeyValueStorageWorldStateStorage storage = emptyStorage();
    storage.updater().putAccountStorageTrieNode(Hash.hash(bytes), bytes).commit();

    assertThat(storage.getNodeData(Hash.hash(bytes))).contains(bytes);
  }

  private KeyValueStorageWorldStateStorage emptyStorage() {
    return new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage());
  }
}
