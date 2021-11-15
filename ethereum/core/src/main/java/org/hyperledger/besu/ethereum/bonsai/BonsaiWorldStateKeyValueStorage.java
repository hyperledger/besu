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
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.javatuples.Pair;

public class BonsaiWorldStateKeyValueStorage implements WorldStateStorage {

  public static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);

  public static final byte[] WORLD_BLOCK_HASH_KEY =
      "worldBlockHash".getBytes(StandardCharsets.UTF_8);

  public static final Bytes CODE_COUNTER_PREFIX =
      Bytes.wrap("codeCounter".getBytes(StandardCharsets.UTF_8));

  protected final KeyValueStorage accountStorage;
  protected final KeyValueStorage codeStorage;
  protected final KeyValueStorage storageStorage;
  protected final KeyValueStorage trieBranchStorage;
  protected final KeyValueStorage trieLogStorage;

  /**
   * Two buckets in order to temporarily store the trie nodes of previous blocks, each store 128.
   * When we are at 128 in the current bucket we empty the other bucket then switch to the other
   * bucket. We use two tables and alternate which ones we insert into so we don't need to track
   * what block the trie nodes were in or whether they were in an orphan or not when we switch
   * buckets we empty out the old one. i.e. pick the bucket based on blocknum (int div) 128 (mod) 2.
   */
  private static final int BUCKET_SIZE = 128;

  protected final Pair<KeyValueStorage, KeyValueStorage> snapTrieBranchBucketsStorage;

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
    snapTrieBranchBucketsStorage =
        Pair.with(
            provider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.TRIE_SNAP_FIRST_BUCKET),
            provider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.TRIE_SNAP_SECOND_BUCKET));
  }

  public BonsaiWorldStateKeyValueStorage(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage,
      final Pair<KeyValueStorage, KeyValueStorage> snapTrieBranchBucketsStorage) {
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
    this.snapTrieBranchBucketsStorage = snapTrieBranchBucketsStorage;
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    if (codeHash != null && !codeHash.equals(Hash.EMPTY)) {
      return codeStorage.get(codeHash.toArrayUnsafe()).map(Bytes::wrap);
    }
    return codeStorage
        .get(accountHash.toArrayUnsafe())
        .flatMap(codeStorage::get)
        .or(
            () ->
                codeStorage.get(
                    accountHash
                        .toArrayUnsafe())) // to manage old bonsai version . it will be removed asap
        .map(Bytes::wrap);
  }

  public Optional<BigInteger> getCodeCounter(final Bytes32 codeHash) {
    return codeStorage
        .get(Bytes.concatenate(CODE_COUNTER_PREFIX, codeHash).toArrayUnsafe())
        .map(BigInteger::new);
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
      return snapTrieBranchBucketsStorage
          .getValue0()
          .get(nodeHash.toArrayUnsafe())
          .or(() -> snapTrieBranchBucketsStorage.getValue1().get(nodeHash.toArrayUnsafe()))
          .or(() -> trieBranchStorage.get(location.toArrayUnsafe()))
          .map(Bytes::wrap);
    }
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return snapTrieBranchBucketsStorage
          .getValue0()
          .get(nodeHash.toArrayUnsafe())
          .or(() -> snapTrieBranchBucketsStorage.getValue1().get(nodeHash.toArrayUnsafe()))
          .or(() -> trieBranchStorage.get(Bytes.concatenate(accountHash, location).toArrayUnsafe()))
          .map(Bytes::wrap);
    }
  }

  public Optional<Bytes> getCodeTrieNode(final Bytes32 codeHash) {
    if (codeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return snapTrieBranchBucketsStorage
          .getValue0()
          .get(codeHash.toArrayUnsafe())
          .or(() -> snapTrieBranchBucketsStorage.getValue1().get(codeHash.toArrayUnsafe()))
          .map(Bytes::wrap)
          .or(() -> getCode(codeHash, null));
    }
  }

  public Optional<byte[]> getTrieLog(final Hash blockHash) {
    return trieLogStorage.get(blockHash.toArrayUnsafe());
  }

  public Optional<Bytes> getStateTrieNode(final Bytes key) {
    return trieBranchStorage.get(key.toArrayUnsafe()).map(Bytes::wrap);
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
    snapTrieBranchBucketsStorage.forEach(
        keyValueStorage -> ((KeyValueStorage) keyValueStorage).clear());
  }

  @Override
  public Updater updater() {
    return new Updater(
        this,
        accountStorage.startTransaction(),
        codeStorage.startTransaction(),
        storageStorage.startTransaction(),
        trieBranchStorage.startTransaction(),
        trieLogStorage.startTransaction(),
        Pair.with(
            snapTrieBranchBucketsStorage.getValue0().startTransaction(),
            snapTrieBranchBucketsStorage.getValue1().startTransaction()));
  }

  public void cleanBucket(final long blockNumber) {
    final int bucketNumber = (int) ((blockNumber / BUCKET_SIZE) % 2);
    final int cleanBucketCounter = BUCKET_SIZE - (int) (blockNumber % BUCKET_SIZE);
    if (cleanBucketCounter == 1) {
      ((bucketNumber == 0)
              ? snapTrieBranchBucketsStorage.getValue1()
              : snapTrieBranchBucketsStorage.getValue0())
          .clear();
    }
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

    private static final Object LOCK = new Object();

    private final KeyValueStorageTransaction accountStorageTransaction;
    private final KeyValueStorageTransaction codeStorageTransaction;
    private final KeyValueStorageTransaction storageStorageTransaction;
    private final KeyValueStorageTransaction trieBranchStorageTransaction;
    private final KeyValueStorageTransaction trieLogStorageTransaction;
    private final Pair<KeyValueStorageTransaction, KeyValueStorageTransaction>
        snapTrieBranchBucketsStorageTransaction;

    final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage;

    public Updater(
        final BonsaiWorldStateKeyValueStorage worldStateKeyValueStorage,
        final KeyValueStorageTransaction accountStorageTransaction,
        final KeyValueStorageTransaction codeStorageTransaction,
        final KeyValueStorageTransaction storageStorageTransaction,
        final KeyValueStorageTransaction trieBranchStorageTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final Pair<KeyValueStorageTransaction, KeyValueStorageTransaction>
            snapTrieBranchBucketsStorageTransaction) {

      this.accountStorageTransaction = accountStorageTransaction;
      this.codeStorageTransaction = codeStorageTransaction;
      this.storageStorageTransaction = storageStorageTransaction;
      this.trieBranchStorageTransaction = trieBranchStorageTransaction;
      this.trieLogStorageTransaction = trieLogStorageTransaction;
      this.snapTrieBranchBucketsStorageTransaction = snapTrieBranchBucketsStorageTransaction;
      this.worldStateKeyValueStorage = worldStateKeyValueStorage;
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
      codeStorageTransaction.put(accountHash.toArrayUnsafe(), codeHash.toArrayUnsafe());
      codeStorageTransaction.put(codeHash.toArrayUnsafe(), code.toArrayUnsafe());
      incCodeCounter(codeHash);
      return this;
    }

    public Updater incCodeCounter(final Bytes32 codeHash) {
      final BigInteger counter =
          worldStateKeyValueStorage.getCodeCounter(codeHash).orElse(BigInteger.ZERO);
      return updateCodeCounter(codeHash, counter.add(BigInteger.ONE));
    }

    public Updater decCodeCounter(final Bytes32 codeHash) {
      final BigInteger counter =
          worldStateKeyValueStorage.getCodeCounter(codeHash).orElse(BigInteger.ZERO);
      return updateCodeCounter(codeHash, counter.subtract(BigInteger.ONE));
    }

    private Updater updateCodeCounter(final Bytes32 codeHash, final BigInteger newCodeCounter) {
      if (newCodeCounter.equals(BigInteger.ZERO)) {
        codeStorageTransaction.remove(
            Bytes.concatenate(CODE_COUNTER_PREFIX, codeHash).toArrayUnsafe());
        codeStorageTransaction.remove(codeHash.toArrayUnsafe());
      } else {
        codeStorageTransaction.put(
            Bytes.concatenate(CODE_COUNTER_PREFIX, codeHash).toArrayUnsafe(),
            newCodeCounter.toByteArray());
      }
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

    public KeyValueStorageTransaction getSnapTrieBranchBucketStorageTransaction(
        final long blockNumber) {
      final int bucketNumber = (int) ((blockNumber / BUCKET_SIZE) % 2);
      return (bucketNumber == 0)
          ? snapTrieBranchBucketsStorageTransaction.getValue0()
          : snapTrieBranchBucketsStorageTransaction.getValue1();
    }

    @Override
    public void commit() {
      accountStorageTransaction.commit();
      codeStorageTransaction.commit();
      storageStorageTransaction.commit();
      trieBranchStorageTransaction.commit();
      trieLogStorageTransaction.commit();
      snapTrieBranchBucketsStorageTransaction.forEach(
          keyValueStorage -> ((KeyValueStorageTransaction) keyValueStorage).commit());
    }

    @Override
    public void rollback() {
      accountStorageTransaction.rollback();
      codeStorageTransaction.rollback();
      storageStorageTransaction.rollback();
      trieBranchStorageTransaction.rollback();
      trieLogStorageTransaction.rollback();
      snapTrieBranchBucketsStorageTransaction.forEach(
          keyValueStorage -> ((KeyValueStorageTransaction) keyValueStorage).rollback());
    }
  }
}
