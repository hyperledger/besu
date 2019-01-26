/*
 * Copyright 2018 ConsenSys AG.
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

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public class KeyValueStorageWorldStateStorage implements WorldStateStorage {

  private final KeyValueStorage keyValueStorage;

  public KeyValueStorageWorldStateStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<BytesValue> getCode(final Bytes32 codeHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(BytesValue.EMPTY);
    } else {
      return keyValueStorage.get(codeHash);
    }
  }

  @Override
  public Optional<BytesValue> getAccountStateTrieNode(final Bytes32 nodeHash) {
    return getTrieNode(nodeHash);
  }

  @Override
  public Optional<BytesValue> getAccountStorageTrieNode(final Bytes32 nodeHash) {
    return getTrieNode(nodeHash);
  }

  private Optional<BytesValue> getTrieNode(final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return keyValueStorage.get(nodeHash);
    }
  }

  @Override
  public Optional<BytesValue> getNodeData(final Bytes32 hash) {
    if (hash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else if (hash.equals(Hash.EMPTY)) {
      return Optional.of(BytesValue.EMPTY);
    } else {
      return keyValueStorage.get(hash);
    }
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements WorldStateStorage.Updater {

    private final KeyValueStorage.Transaction transaction;

    public Updater(final KeyValueStorage.Transaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public Updater putCode(final Bytes32 codeHash, final BytesValue code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      transaction.put(codeHash, code);
      return this;
    }

    @Override
    public Updater putAccountStateTrieNode(final Bytes32 nodeHash, final BytesValue node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      transaction.put(nodeHash, node);
      return this;
    }

    @Override
    public Updater putAccountStorageTrieNode(final Bytes32 nodeHash, final BytesValue node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      transaction.put(nodeHash, node);
      return this;
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }
  }
}
