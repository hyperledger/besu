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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

public class BonsaiWorldStateKeyValueStorage implements WorldStateStorage {

  private static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  private static final byte[] WORLD_BLOCK_HASH_KEY = "worldBlockHash".getBytes(StandardCharsets.UTF_8);

  private final KeyValueStorage accountStorage;
  private final KeyValueStorage codeStorage;
  private final KeyValueStorage storageStorage;
  private final KeyValueStorage trieBranchStorage;
  private final KeyValueStorage trieLogStorage;

  private final ReentrantLock lock = new ReentrantLock();

  public BonsaiWorldStateKeyValueStorage(final StorageProvider provider) {
    accountStorage = provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
            codeStorage = provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
            storageStorage = provider.getStorageBySegmentIdentifier(
                       KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
            trieBranchStorage  = provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
            trieLogStorage = provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);

  }

  public static byte[] getWorldRootHashKey() {
    return WORLD_ROOT_HASH_KEY;
  }

  public static byte[] getWorldBlockHashKey() {
    return WORLD_BLOCK_HASH_KEY;
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash, final Address address) {
    return codeStorage.get(address.toArrayUnsafe()).map(Bytes::wrap);
  }

  public Optional<Bytes> getAccount(final Address address) {
    return accountStorage.get(address.toArrayUnsafe()).map(Bytes::wrap);
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
  public Optional<Bytes> getAccountStorageTrieNode(final Address address, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage.get(Bytes.concatenate(address, location).toArrayUnsafe()).map(Bytes::wrap);
    }
  }

  public Optional<Bytes> getStateTrieNode(final Bytes location){
    return trieBranchStorage.get(location.toArrayUnsafe()).map(Bytes::wrap);
  }

  public Optional<Bytes> getWorldStateRootHash(){
    return trieBranchStorage.get(WORLD_ROOT_HASH_KEY).map(Bytes::wrap);
  }

  public Optional<Bytes> getWorldStateBlockHash(){
    return trieBranchStorage.get(WORLD_BLOCK_HASH_KEY).map(Bytes::wrap);
  }

  @Override
  public Optional<Bytes> getNodeData(final Bytes location, final Bytes32 hash) {
    return Optional.empty();
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash) {
    return getWorldStateRootHash().isPresent();
  }

  @Override
  public Updater updater() {
    return new Updater(accountStorage.startTransaction(),
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


    public Updater(KeyValueStorageTransaction accountStorageTransaction,
                   KeyValueStorageTransaction codeStorageTransaction,
                   KeyValueStorageTransaction storageStorageTransaction,
                   KeyValueStorageTransaction trieBranchStorageTransaction,
                   KeyValueStorageTransaction trieLogStorageTransaction) {

      this.accountStorageTransaction = accountStorageTransaction;
      this.codeStorageTransaction = codeStorageTransaction;
      this.storageStorageTransaction = storageStorageTransaction;
      this.trieBranchStorageTransaction = trieBranchStorageTransaction;
      this.trieLogStorageTransaction = trieLogStorageTransaction;
    }

    public Updater removeCode(final Address address) {
      codeStorageTransaction.remove(address.toArrayUnsafe());
      return this;
    }

    @Override
    public Updater putCode(final Address address, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      codeStorageTransaction.put(address.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    public Updater removeAccountInfoState(final Address address) {
      accountStorageTransaction.remove(address.toArrayUnsafe());
      return this;
    }

    public Updater putAccountInfoState(final Address address, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      accountStorageTransaction.put(address.toArrayUnsafe(), accountValue.toArrayUnsafe());
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
        final Address address, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageTransaction.put(Bytes.concatenate(address,location).toArrayUnsafe(), node.toArrayUnsafe());
      return this;
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
