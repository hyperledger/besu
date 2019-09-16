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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage.Updater;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.junit.Test;

public class KeyValueStorageWorldStateStorageTest {

  @Test
  public void getCode_returnsEmpty() {
    final WorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getCode(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getAccountStateTrieNode_returnsEmptyNode() {
    final WorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getAccountStateTrieNode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getAccountStorageTrieNode_returnsEmptyNode() {
    final WorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getAccountStorageTrieNode(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getNodeData_returnsEmptyValue() {
    final WorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getNodeData(Hash.EMPTY)).contains(BytesValue.EMPTY);
  }

  @Test
  public void getNodeData_returnsEmptyNode() {
    final WorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getNodeData(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getCode_saveAndGetSpecialValues() {
    final WorldStateKeyValueStorage storage = emptyStorage();
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
    final BytesValue bytes = BytesValue.fromHexString("0x123456");
    final WorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putCode(bytes).commit();

    assertThat(storage.getCode(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetSpecialValues() {
    final WorldStateKeyValueStorage storage = emptyStorage();
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
    final BytesValue bytes = BytesValue.fromHexString("0x123456");
    final WorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStateTrieNode(Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetSpecialValues() {
    final WorldStateKeyValueStorage storage = emptyStorage();
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
    final BytesValue bytes = BytesValue.fromHexString("0x123456");
    final WorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStorageTrieNode(Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getNodeData_saveAndGetSpecialValues() {
    final WorldStateKeyValueStorage storage = emptyStorage();
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
    final BytesValue bytes = BytesValue.fromHexString("0x123456");
    final WorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStorageTrieNode(Hash.hash(bytes), bytes).commit();

    assertThat(storage.getNodeData(Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void reconcilesNonConflictingUpdaters() {
    final BytesValue bytesA = BytesValue.fromHexString("0x12");
    final BytesValue bytesB = BytesValue.fromHexString("0x1234");
    final BytesValue bytesC = BytesValue.fromHexString("0x123456");

    final WorldStateKeyValueStorage storage = emptyStorage();
    final Updater updaterA = storage.updater();
    final Updater updaterB = storage.updater();

    updaterA.putCode(bytesA);
    updaterB.putCode(bytesA);
    updaterB.putCode(bytesB);
    updaterA.putCode(bytesC);

    updaterA.commit();
    updaterB.commit();

    assertThat(storage.getCode(Hash.hash(bytesA))).contains(bytesA);
    assertThat(storage.getCode(Hash.hash(bytesB))).contains(bytesB);
    assertThat(storage.getCode(Hash.hash(bytesC))).contains(bytesC);
  }

  @Test
  public void isWorldStateAvailable_defaultIsFalse() {
    assertThat(emptyStorage().isWorldStateAvailable(Bytes32.TRUE)).isFalse();
  }

  @Test
  public void isWorldStateAvailable_emptyTrieStateAlwaysAvailable() {
    assertThat(emptyStorage().isWorldStateAvailable(Hash.EMPTY_TRIE_HASH)).isTrue();
  }

  private WorldStateKeyValueStorage emptyStorage() {
    return new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
  }
}
