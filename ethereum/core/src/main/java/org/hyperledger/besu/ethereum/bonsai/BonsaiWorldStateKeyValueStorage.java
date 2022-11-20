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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.util.RangeManager.generateRangeFromLocation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.PeerTrieNodeFinder;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiWorldStateKeyValueStorage implements WorldStateStorage {
  public static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  public static final byte[] WORLD_BLOCK_HASH_KEY =
      "worldBlockHash".getBytes(StandardCharsets.UTF_8);

  protected final KeyValueStorage accountStorage;
  protected final KeyValueStorage codeStorage;
  protected final KeyValueStorage storageStorage;
  protected final KeyValueStorage trieBranchStorage;
  protected final KeyValueStorage trieLogStorage;

  private Optional<PeerTrieNodeFinder> maybeFallbackNodeFinder;

  public BonsaiWorldStateKeyValueStorage(final StorageProvider provider) {
    this(
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE),
        Optional.empty());
  }

  public BonsaiWorldStateKeyValueStorage(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    this(
        accountStorage,
        codeStorage,
        storageStorage,
        trieBranchStorage,
        trieLogStorage,
        Optional.empty());
  }

  public BonsaiWorldStateKeyValueStorage(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage,
      final Optional<PeerTrieNodeFinder> fallbackNodeFinder) {
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
    this.maybeFallbackNodeFinder = fallbackNodeFinder;
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    return codeStorage.get(accountHash.toArrayUnsafe()).map(Bytes::wrap);
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return accountStorage.get(accountHash.toArrayUnsafe()).map(Bytes::wrap);
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
      return trieBranchStorage
          .get(location.toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
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
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
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
    return storageStorage
        .get(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe())
        .map(Bytes::wrap);
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

  public void pruneAccountState(final Bytes location, final Optional<Bytes> maybeExclude) {
    final Pair<Bytes, Bytes> range = generateRangeFromLocation(Bytes.EMPTY, location);
    final AtomicInteger eltRemoved = new AtomicInteger();
    final AtomicReference<KeyValueStorageTransaction> nodeUpdaterTmp =
        new AtomicReference<>(accountStorage.startTransaction());

    // cleaning the account trie node in this location
    pruneTrieNode(Bytes.EMPTY, location, maybeExclude);

    // cleaning the account flat database by searching for the keys that are in the range
    accountStorage
        .getInRange(range.getFirst(), range.getSecond())
        .forEach(
            (key, value) -> {
              final boolean shouldExclude =
                  maybeExclude
                      .filter(
                          bytes ->
                              bytes.commonPrefixLength(CompactEncoding.bytesToPath(key))
                                  == bytes.size())
                      .isPresent();
              if (!shouldExclude) {
                // clean the storage and code of the deleted account
                pruneStorageState(key, Bytes.EMPTY, Optional.empty());
                pruneCodeState(key);
                nodeUpdaterTmp.get().remove(key.toArrayUnsafe());
                if (eltRemoved.getAndIncrement() % 100 == 0) {
                  nodeUpdaterTmp.get().commit();
                  nodeUpdaterTmp.set(accountStorage.startTransaction());
                }
              }
            });
    nodeUpdaterTmp.get().commit();
  }

  public void pruneStorageState(
      final Bytes accountHash, final Bytes location, final Optional<Bytes> maybeExclude) {
    final Pair<Bytes, Bytes> range = generateRangeFromLocation(accountHash, location);
    final AtomicInteger eltRemoved = new AtomicInteger();
    final AtomicReference<KeyValueStorageTransaction> nodeUpdaterTmp =
        new AtomicReference<>(storageStorage.startTransaction());

    // cleaning the storage trie node in this location
    pruneTrieNode(accountHash, location, maybeExclude);

    // cleaning the storage flat database by searching for the keys that are in the range
    storageStorage
        .getInRange(range.getFirst(), range.getSecond())
        .forEach(
            (key, value) -> {
              final boolean shouldExclude =
                  maybeExclude
                      .filter(
                          bytes ->
                              bytes.commonPrefixLength(
                                      CompactEncoding.bytesToPath(key).slice(Bytes32.SIZE * 2))
                                  == bytes.size())
                      .isPresent();
              // System.out.println("found with method " + index + " to remove accountHash " +
              // accountHash + " " + key + " from " + range.getLeft() + " to " + range.getRight() +
              // " for data " + data + " and location " + location);
              if (!shouldExclude) {
                nodeUpdaterTmp.get().remove(key.toArrayUnsafe());
                if (eltRemoved.getAndIncrement() % 100 == 0) {
                  nodeUpdaterTmp.get().commit();
                  nodeUpdaterTmp.set(storageStorage.startTransaction());
                }
              }
            });
    nodeUpdaterTmp.get().commit();
  }

  public void pruneCodeState(final Bytes accountHash) {
    final KeyValueStorageTransaction transaction = codeStorage.startTransaction();
    transaction.remove(accountHash.toArrayUnsafe());
    transaction.commit();
  }

  private void pruneTrieNode(
      final Bytes accountHash, final Bytes location, final Optional<Bytes> maybeExclude) {
    final AtomicInteger eltRemoved = new AtomicInteger();
    final AtomicReference<KeyValueStorageTransaction> nodeUpdaterTmp =
        new AtomicReference<>(trieBranchStorage.startTransaction());
    trieBranchStorage
        .getByPrefix(Bytes.concatenate(accountHash, location))
        .forEach(
            (key) -> {
              final boolean shouldExclude =
                  maybeExclude
                      .filter(
                          bytes ->
                              bytes.commonPrefixLength(key.slice(accountHash.size()))
                                  == bytes.size())
                      .isPresent();
              // System.out.println("found with method " + index + " to remove trie node " +
              // accountHash + " " + key + " from " + Bytes.concatenate(accountHash,location) + "
              // for data " + data + " and location " + location);
              if (!shouldExclude) {
                nodeUpdaterTmp.get().remove(key.toArrayUnsafe());
                if (eltRemoved.getAndIncrement() % 100 == 0) {
                  nodeUpdaterTmp.get().commit();
                  nodeUpdaterTmp.set(trieBranchStorage.startTransaction());
                }
              }
            });
    nodeUpdaterTmp.get().commit();
  }

  @Override
  public BonsaiUpdater updater() {
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

  public Optional<PeerTrieNodeFinder> getMaybeFallbackNodeFinder() {
    return maybeFallbackNodeFinder;
  }

  public void useFallbackNodeFinder(final Optional<PeerTrieNodeFinder> maybeFallbackNodeFinder) {
    checkNotNull(maybeFallbackNodeFinder);
    this.maybeFallbackNodeFinder = maybeFallbackNodeFinder;
  }

  public interface BonsaiUpdater extends WorldStateStorage.Updater {
    BonsaiUpdater removeCode(final Hash accountHash);

    BonsaiUpdater removeAccountInfoState(final Hash accountHash);

    BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue);

    BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage);

    void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash);

    KeyValueStorageTransaction getTrieBranchStorageTransaction();

    KeyValueStorageTransaction getTrieLogStorageTransaction();
  }

  public static class Updater implements BonsaiUpdater {

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

    @Override
    public BonsaiUpdater removeCode(final Hash accountHash) {
      codeStorageTransaction.remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putCode(final Hash accountHash, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      codeStorageTransaction.put(accountHash.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountInfoState(final Hash accountHash) {
      accountStorageTransaction.remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
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
    public BonsaiUpdater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageTransaction.put(location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
      trieBranchStorageTransaction.remove(location.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageTransaction.put(
          Bytes.concatenate(accountHash, location).toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      storageStorageTransaction.put(
          Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(), storage.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      storageStorageTransaction.remove(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
    }

    @Override
    public KeyValueStorageTransaction getTrieBranchStorageTransaction() {
      return trieBranchStorageTransaction;
    }

    @Override
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
