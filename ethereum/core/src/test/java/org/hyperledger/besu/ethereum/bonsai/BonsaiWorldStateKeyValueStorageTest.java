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
package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class BonsaiWorldStateKeyValueStorageTest {

  @Test
  public void getCode_returnsEmpty() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getCode(null, Hash.EMPTY)).isEmpty();
  }

  @Test
  public void getAccountStateTrieNode_returnsEmptyNode() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(
            storage.getAccountStateTrieNode(Bytes.EMPTY, MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getAccountStorageTrieNode_returnsEmptyNode() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(
            storage.getAccountStorageTrieNode(
                Hash.EMPTY, Bytes.EMPTY, MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getNodeData_returnsEmptyValue() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getNodeData(null, null)).isEmpty();
  }

  @Test
  public void getNodeData_returnsEmptyNode() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    assertThat(storage.getNodeData(Bytes.EMPTY, MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)).isEmpty();
  }

  @Test
  public void getCode_saveAndGetSpecialValues() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putCode(Hash.EMPTY, MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putCode(Hash.EMPTY, Bytes.EMPTY)
        .commit();

    assertThat(storage.getCode(null, Hash.EMPTY)).contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
  }

  @Test
  public void getCode_saveAndGetRegularValue() {
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putCode(Hash.EMPTY, bytes).commit();

    assertThat(storage.getCode(null, Hash.EMPTY)).contains(bytes);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetSpecialValues() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStateTrieNode(
            Bytes.EMPTY,
            Hash.hash(MerklePatriciaTrie.EMPTY_TRIE_NODE),
            MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putAccountStateTrieNode(Bytes.EMPTY, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(
            storage.getAccountStateTrieNode(Bytes.EMPTY, MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStateTrieNode(Bytes.EMPTY, Hash.EMPTY)).contains(Bytes.EMPTY);
  }

  @Test
  public void getAccountStateTrieNode_saveAndGetRegularValue() {
    final Bytes location = Bytes.fromHexString("0x01");
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    storage.updater().putAccountStateTrieNode(location, Hash.hash(bytes), bytes).commit();

    assertThat(storage.getAccountStateTrieNode(location, Hash.hash(bytes))).contains(bytes);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetSpecialValues() {
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStorageTrieNode(
            Hash.EMPTY,
            Bytes.EMPTY,
            Hash.hash(MerklePatriciaTrie.EMPTY_TRIE_NODE),
            MerklePatriciaTrie.EMPTY_TRIE_NODE)
        .putAccountStorageTrieNode(Hash.EMPTY, Bytes.EMPTY, Hash.hash(Bytes.EMPTY), Bytes.EMPTY)
        .commit();

    assertThat(
            storage.getAccountStorageTrieNode(
                Hash.EMPTY, Bytes.EMPTY, Hash.hash(MerklePatriciaTrie.EMPTY_TRIE_NODE)))
        .contains(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    assertThat(storage.getAccountStorageTrieNode(Hash.EMPTY, Bytes.EMPTY, Hash.EMPTY))
        .contains(Bytes.EMPTY);
  }

  @Test
  public void getAccountStorageTrieNode_saveAndGetRegularValue() {
    final Hash accountHash = Hash.hash(Address.fromHexString("0x1"));
    final Bytes location = Bytes.fromHexString("0x01");
    final Bytes bytes = Bytes.fromHexString("0x123456");
    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    storage
        .updater()
        .putAccountStorageTrieNode(accountHash, location, Hash.hash(bytes), bytes)
        .commit();

    assertThat(storage.getAccountStorageTrieNode(accountHash, location, Hash.hash(bytes)))
        .contains(bytes);
  }

  @Test
  public void reconcilesNonConflictingUpdaters() {
    final Hash accountHashA = Hash.hash(Address.fromHexString("0x1"));
    final Hash accountHashB = Hash.hash(Address.fromHexString("0x2"));
    final Hash accountHashD = Hash.hash(Address.fromHexString("0x4"));
    final Bytes bytesA = Bytes.fromHexString("0x12");
    final Bytes bytesB = Bytes.fromHexString("0x1234");
    final Bytes bytesC = Bytes.fromHexString("0x123456");

    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    final BonsaiWorldStateKeyValueStorage.Updater updaterA = storage.updater();
    final BonsaiWorldStateKeyValueStorage.Updater updaterB = storage.updater();

    updaterA.putCode(accountHashA, bytesA);
    updaterB.putCode(accountHashB, bytesA);
    updaterB.putCode(accountHashB, bytesB);
    updaterA.putCode(accountHashD, bytesC);

    updaterA.commit();
    updaterB.commit();

    assertThat(storage.getCode(null, accountHashA)).contains(bytesA);
    assertThat(storage.getCode(null, accountHashB)).contains(bytesB);
    assertThat(storage.getCode(null, accountHashD)).contains(bytesC);
  }

  @Test
  public void isWorldStateAvailable_defaultIsFalse() {
    assertThat(emptyStorage().isWorldStateAvailable(UInt256.valueOf(1), Hash.EMPTY)).isFalse();
  }

  @Test
  public void isWorldStateAvailable_StateAvailableByRootHash() {

    final BonsaiWorldStateKeyValueStorage storage = emptyStorage();
    final BonsaiWorldStateKeyValueStorage.Updater updater = storage.updater();
    final Bytes rootHashKey = Bytes32.fromHexString("0x01");
    updater.getTrieBranchStorageTransaction().put(WORLD_ROOT_HASH_KEY, rootHashKey.toArrayUnsafe());
    updater.commit();
    assertThat(storage.isWorldStateAvailable(Hash.wrap(Bytes32.wrap(rootHashKey)), Hash.EMPTY))
        .isTrue();
  }

  private BonsaiWorldStateKeyValueStorage emptyStorage() {
    return new BonsaiWorldStateKeyValueStorage(new InMemoryKeyValueStorageProvider());
  }
}
