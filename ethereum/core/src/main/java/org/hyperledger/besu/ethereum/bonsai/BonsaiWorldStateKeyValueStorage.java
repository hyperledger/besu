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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

public class BonsaiWorldStateKeyValueStorage implements WorldStateStorage {
  public static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  public static final byte[] WORLD_BLOCK_HASH_KEY =
      "worldBlockHash".getBytes(StandardCharsets.UTF_8);

  protected final KeyValueStorage accountStorage;
  protected final KeyValueStorage codeStorage;
  protected final KeyValueStorage storageStorage;
  protected final KeyValueStorage trieBranchStorage;
  protected final KeyValueStorage trieLogStorage;

  public BonsaiWorldStateKeyValueStorage(final StorageProvider provider) {
    accountStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    codeStorage = provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    storageStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    trieBranchStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    trieLogStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
  }

  public BonsaiWorldStateKeyValueStorage(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    return codeStorage.get(accountHash.toArrayUnsafe()).map(Bytes::wrap);
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    Optional<Bytes> response = accountStorage.get(accountHash.toArrayUnsafe()).map(Bytes::wrap);
    if (response.isEmpty()) {
      // after a snapsync/fastsync we only have the trie branches.
      final Optional<Bytes> worldStateRootHash = getWorldStateRootHash();
      if (worldStateRootHash.isPresent()) {
        response =
            new StoredMerklePatriciaTrie<>(
                    new StoredNodeFactory<>(
                        this::getAccountStateTrieNode, Function.identity(), Function.identity()),
                    Bytes32.wrap(worldStateRootHash.get()))
                .get(accountHash);
      }
    }
    return response;
  }

  @Override
  public Optional<Bytes> getAccountTrieNodeData(final Bytes location, final Bytes32 hash) {
    // for Bonsai trie fast sync this method should return an empty
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage.get(location.toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage
          .get(Bytes.concatenate(accountHash, location).toArrayUnsafe())
          .map(Bytes::wrap);
    }
  }

  public Optional<byte[]> getTrieLog(final Hash blockHash) {
    return trieLogStorage.get(blockHash.toArrayUnsafe());
  }

  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    return trieBranchStorage.get(location.toArrayUnsafe()).map(Bytes::wrap);
  }

  public Optional<Bytes> getWorldStateRootHash() {
    return trieBranchStorage.get(WORLD_ROOT_HASH_KEY).map(Bytes::wrap);
  }

  public Optional<Bytes> getWorldStateBlockHash() {
    return trieBranchStorage.get(WORLD_BLOCK_HASH_KEY).map(Bytes::wrap);
  }

  public Optional<Bytes> getStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
    Optional<Bytes> response =
        storageStorage
            .get(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe())
            .map(Bytes::wrap);
    if (response.isEmpty()) {
      // after a snapsync/fastsync we only have the trie branches.
      final Optional<Bytes> account = getAccount(accountHash);
      final Optional<Bytes> worldStateRootHash = getWorldStateRootHash();
      if (account.isPresent() && worldStateRootHash.isPresent()) {
        final StateTrieAccountValue accountValue =
            StateTrieAccountValue.readFrom(
                org.hyperledger.besu.ethereum.rlp.RLP.input(account.get()));
        response =
            new StoredMerklePatriciaTrie<>(
                    new StoredNodeFactory<>(
                        (location, hash) -> getAccountStorageTrieNode(accountHash, location, hash),
                        Function.identity(),
                        Function.identity()),
                    accountValue.getStorageRoot())
                .get(slotHash)
                .map(bytes -> Bytes32.leftPad(RLP.decodeValue(bytes)));
      }
    }
    return response;
  }

  @Override
  public Optional<Bytes> getNodeData(final Bytes location, final Bytes32 hash) {
    return Optional.empty();
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash, final Hash blockHash) {
    return trieBranchStorage
            .get(WORLD_ROOT_HASH_KEY)
            .map(Bytes32::wrap)
            .filter(hash -> hash.equals(rootHash))
            .isPresent()
        || trieLogStorage.containsKey(blockHash.toArrayUnsafe());
  }

  @Override
  public void clear() {
    accountStorage.clear();
    codeStorage.clear();
    storageStorage.clear();
    trieBranchStorage.clear();
    trieLogStorage.clear();
  }

  @Override
  public void clearFlatDatabase() {
    accountStorage.clear();
    storageStorage.clear();
  }

  @Override
  public Updater updater() {
    return new Updater(
        accountStorage.startTransaction(),
        codeStorage.startTransaction(),
        storageStorage.startTransaction(),
        trieBranchStorage.startTransaction(),
        trieLogStorage.startTransaction());
  }

  @Override
  public long prune(final Predicate<byte[]> inUseCheck) {
    throw new RuntimeException("Bonsai Tries do not work with pruning.");
  }

  @Override
  public long addNodeAddedListener(final NodesAddedListener listener) {
    throw new RuntimeException("addNodeAddedListener not available");
  }

  @Override
  public void removeNodeAddedListener(final long id) {
    throw new RuntimeException("removeNodeAddedListener not available");
  }

  public static class Updater implements WorldStateStorage.Updater {

    private final KeyValueStorageTransaction accountStorageTransaction;
    private final KeyValueStorageTransaction codeStorageTransaction;
    private final KeyValueStorageTransaction storageStorageTransaction;
    private final KeyValueStorageTransaction trieBranchStorageTransaction;
    private final KeyValueStorageTransaction trieLogStorageTransaction;

    public Updater(
        final KeyValueStorageTransaction accountStorageTransaction,
        final KeyValueStorageTransaction codeStorageTransaction,
        final KeyValueStorageTransaction storageStorageTransaction,
        final KeyValueStorageTransaction trieBranchStorageTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction) {

      this.accountStorageTransaction = accountStorageTransaction;
      this.codeStorageTransaction = codeStorageTransaction;
      this.storageStorageTransaction = storageStorageTransaction;
      this.trieBranchStorageTransaction = trieBranchStorageTransaction;
      this.trieLogStorageTransaction = trieLogStorageTransaction;
    }

    public Updater removeCode(final Hash accountHash) {
      codeStorageTransaction.remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putCode(final Hash accountHash, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      codeStorageTransaction.put(accountHash.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    public Updater removeAccountInfoState(final Hash accountHash) {
      accountStorageTransaction.remove(accountHash.toArrayUnsafe());
      return this;
    }

    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      accountStorageTransaction.put(accountHash.toArrayUnsafe(), accountValue.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater saveWorldState(
        final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      trieBranchStorageTransaction.put(Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      trieBranchStorageTransaction.put(WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      trieBranchStorageTransaction.put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageTransaction.put(location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater removeAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
      trieBranchStorageTransaction.remove(location.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageTransaction.put(
          Bytes.concatenate(accountHash, location).toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    public Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      storageStorageTransaction.put(
          Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(), storage.toArrayUnsafe());
      return this;
    }

    public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
      storageStorageTransaction.remove(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
    }

    public KeyValueStorageTransaction getTrieBranchStorageTransaction() {
      return trieBranchStorageTransaction;
    }

    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorageTransaction;
    }

    @Override
    public void commit() {
      accountStorageTransaction.commit();
      codeStorageTransaction.commit();
      storageStorageTransaction.commit();
      trieBranchStorageTransaction.commit();
      trieLogStorageTransaction.commit();
    }

    @Override
    public void rollback() {
      accountStorageTransaction.rollback();
      codeStorageTransaction.rollback();
      storageStorageTransaction.rollback();
      trieBranchStorageTransaction.rollback();
      trieLogStorageTransaction.rollback();
    }
  }
}
