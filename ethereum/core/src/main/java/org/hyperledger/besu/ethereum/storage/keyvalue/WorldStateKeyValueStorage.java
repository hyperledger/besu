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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.Subscribers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class WorldStateKeyValueStorage implements WorldStateStorage {

  private final Subscribers<NodesAddedListener> nodeAddedListeners = Subscribers.create();
  private final KeyValueStorage keyValueStorage;

  public WorldStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return keyValueStorage.get(codeHash.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes32 nodeHash) {
    return getTrieNode(nodeHash);
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(final Bytes32 nodeHash) {
    return getTrieNode(nodeHash);
  }

  private Optional<Bytes> getTrieNode(final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return keyValueStorage.get(nodeHash.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  @Override
  public Optional<Bytes> getNodeData(final Bytes32 hash) {
    if (hash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else if (hash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return keyValueStorage.get(hash.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash) {
    return getAccountStateTrieNode(rootHash).isPresent();
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.startTransaction(), nodeAddedListeners);
  }

  @Override
  public long prune(final Predicate<byte[]> inUseCheck) {
    return keyValueStorage.removeAllKeysUnless(inUseCheck);
  }

  @Override
  public long addNodeAddedListener(final NodesAddedListener listener) {
    return nodeAddedListeners.subscribe(listener);
  }

  @Override
  public void removeNodeAddedListener(final long id) {
    nodeAddedListeners.unsubscribe(id);
  }

  public static class Updater implements WorldStateStorage.Updater {

    private final KeyValueStorageTransaction transaction;
    private final Subscribers<NodesAddedListener> nodeAddedListeners;
    private final List<Bytes32> addedNodes = new ArrayList<>();

    public Updater(
        final KeyValueStorageTransaction transaction,
        final Subscribers<NodesAddedListener> nodeAddedListeners) {
      this.transaction = transaction;
      this.nodeAddedListeners = nodeAddedListeners;
    }

    @Override
    public Updater removeAccountStateTrieNode(final Bytes32 nodeHash) {
      transaction.remove(nodeHash.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putCode(final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }

      addedNodes.add(codeHash);
      transaction.put(codeHash.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putAccountStateTrieNode(final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      addedNodes.add(nodeHash);
      transaction.put(nodeHash.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putAccountStorageTrieNode(final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      addedNodes.add(nodeHash);
      transaction.put(nodeHash.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public void commit() {
      nodeAddedListeners.forEach(listener -> listener.onNodesAdded(addedNodes));
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }
  }
}
