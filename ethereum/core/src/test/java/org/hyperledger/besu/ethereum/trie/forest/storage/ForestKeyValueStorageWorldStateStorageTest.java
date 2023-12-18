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
 */
package org.hyperledger.besu.ethereum.trie.forest.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage.Updater;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class ForestKeyValueStorageWorldStateStorageTest {

  @Test
  public void getCode_returnsEmpty() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getCode(Hash.EMPTY, null)).contains(Bytes.EMPTY);
  }

  @Test
  public void getAccountStateTrieNode_returnsEmptyNode() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getAccountStorageTrieNode_returnsEmptyNode() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(
            storage.getAccountStorageTrieNode(null, Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getNodeData_returnsEmptyValue() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getNodeData(null, Hash.EMPTY)).contains(Bytes.EMPTY);
  }

  @Test
  public void getNodeData_returnsEmptyNode() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getNodeData(Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getCode_saveAndGetSpecialValues() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putCode(null, MerkleTrie.EMPTY_TRIE_NODE).putCode(null, Bytes.EMPTY).commit();

    assertThat(storage.getCode(MerkleTrie.EMPTY_TRIE_NODE_HASH, null))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getCode(Hash.EMPTY, null)).contains(Bytes.EMPTY);
  }

  @Test
  public void getCode_saveAndGetRegularValue() {
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putCode(null, bytes).commit();

    assertThat(storage.getCode(Hash.hash(bytes), null)).contains(bytes);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetSpecialValues() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStateTrieNode(
            null, Hash.hash(MerkleTrie.EMPTY_TRIE_NODE), MerkleTrie.EMPTY_TRIE_NODE)
        .putAccountStateTrieNode(null, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, Hash.EMPTY)).contains(Bytes.EMPTY);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetRegularValue() {
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStateTrieNode(null, Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetSpecialValues() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStorageTrieNode(
            null, null, Hash.hash(MerkleTrie.EMPTY_TRIE_NODE), MerkleTrie.EMPTY_TRIE_NODE)
        .putAccountStorageTrieNode(null, null, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(
            storage.getAccountStorageTrieNode(null, Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStorageTrieNode(null, Bytes.EMPTY, Hash.EMPTY))
        .contains(Bytes.EMPTY);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetRegularValue() {
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStorageTrieNode(null, null, Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getNodeData_saveAndGetSpecialValues() {
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStorageTrieNode(
            null, null, Hash.hash(MerkleTrie.EMPTY_TRIE_NODE), MerkleTrie.EMPTY_TRIE_NODE)
        .putAccountStorageTrieNode(null, null, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(storage.getNodeData(Bytes.EMPTY, MerkleTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerkleTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getNodeData(Bytes.EMPTY, Hash.EMPTY)).contains(Bytes.EMPTY);
  }

  @Test
  public void getNodeData_saveAndGetRegularValue() {
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStorageTrieNode(null, null, Hash.hash(bytes), bytes).commit();

    assertThat(storage.getNodeData(null, Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void reconcilesNonConflictingUpdaters() {
    final Bytes bytesA = Bytes.fromHexString("0x12");
    final Bytes bytesB = Bytes.fromHexString("0x1234");
    final Bytes bytesC = Bytes.fromHexString("0x123456");

    final ForestWorldStateKeyValueStorage storage = emptyStorage();
    final Updater updaterA = storage.updater();
    final Updater updaterB = storage.updater();

    updaterA.putCode(null, bytesA);
    updaterB.putCode(null, bytesA);
    updaterB.putCode(null, bytesB);
    updaterA.putCode(null, bytesC);

    updaterA.commit();
    updaterB.commit();

    assertThat(storage.getCode(Hash.hash(bytesA), null)).contains(bytesA);
    assertThat(storage.getCode(Hash.hash(bytesB), null)).contains(bytesB);
    assertThat(storage.getCode(Hash.hash(bytesC), null)).contains(bytesC);
  }

  @Test
  public void isWorldStateAvailable_defaultIsFalse() {
    assertThat(emptyStorage().isWorldStateAvailable(UInt256.valueOf(1), null)).isFalse();
  }

  @Test
  public void isWorldStateAvailable_emptyTrieStateAlwaysAvailable() {
    assertThat(emptyStorage().isWorldStateAvailable(Hash.EMPTY_TRIE_HASH, null)).isTrue();
  }

  private ForestWorldStateKeyValueStorage emptyStorage() {
    return new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
  }
}
