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
package org.hyperledger.besu.ethereum.trie.bonsai.storage;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.flat.FlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.FlatWorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.util.Subscribers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class BonsaiWorldStateKeyValueStorage implements FlatWorldStateStorage, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(BonsaiWorldStateKeyValueStorage.class);

  // 0x776f726c64526f6f74
  public static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);
  // 0x776f726c64426c6f636b48617368
  public static final byte[] WORLD_BLOCK_HASH_KEY =
      "worldBlockHash".getBytes(StandardCharsets.UTF_8);

  protected final FlatDbStrategyProvider flatDbStrategyProvider;

  protected final SegmentedKeyValueStorage composedWorldStateStorage;
  protected final KeyValueStorage trieLogStorage;

  private final AtomicBoolean shouldClose = new AtomicBoolean(false);

  protected final AtomicBoolean isClosed = new AtomicBoolean(false);

  protected final Subscribers<BonsaiStorageSubscriber> subscribers = Subscribers.create();

  public BonsaiWorldStateKeyValueStorage(
      final StorageProvider provider,
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration) {
    this.composedWorldStateStorage =
        provider.getStorageBySegmentIdentifiers(
            List.of(
                ACCOUNT_INFO_STATE, CODE_STORAGE, ACCOUNT_STORAGE_STORAGE, TRIE_BRANCH_STORAGE));
    this.trieLogStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
    this.flatDbStrategyProvider =
        new FlatDbStrategyProvider(metricsSystem, dataStorageConfiguration);
    flatDbStrategyProvider.loadFlatDbStrategy(composedWorldStateStorage);
  }

  public BonsaiWorldStateKeyValueStorage(
      final FlatDbStrategyProvider flatDbStrategyProvider,
      final SegmentedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage) {
    this.flatDbStrategyProvider = flatDbStrategyProvider;
    this.composedWorldStateStorage = composedWorldStateStorage;
    this.trieLogStorage = trieLogStorage;
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.BONSAI;
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return flatDbStrategyProvider.getFlatDbMode();
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return flatDbStrategyProvider
          .getFlatDbStrategy(composedWorldStateStorage)
          .getFlatCode(codeHash, accountHash, composedWorldStateStorage);
    }
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return flatDbStrategyProvider
        .getFlatDbStrategy(composedWorldStateStorage)
        .getFlatAccount(
            this::getWorldStateRootHash,
            this::getAccountStateTrieNode,
            accountHash,
            composedWorldStateStorage);
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return composedWorldStateStorage
          .get(TRIE_BRANCH_STORAGE, location.toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
    }
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return composedWorldStateStorage
          .get(TRIE_BRANCH_STORAGE, Bytes.concatenate(accountHash, location).toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
    }
  }

  @Override
  public Optional<Bytes> getTrieNodeUnsafe(final Bytes key) {
    return composedWorldStateStorage
        .get(TRIE_BRANCH_STORAGE, Bytes.concatenate(key).toArrayUnsafe())
        .map(Bytes::wrap);
  }

  public Optional<byte[]> getTrieLog(final Hash blockHash) {
    return trieLogStorage.get(blockHash.toArrayUnsafe());
  }

  public Stream<byte[]> streamTrieLogKeys(final long limit) {
    return trieLogStorage.streamKeys().limit(limit);
  }

  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    return composedWorldStateStorage
        .get(TRIE_BRANCH_STORAGE, location.toArrayUnsafe())
        .map(Bytes::wrap);
  }

  public Optional<Bytes> getWorldStateRootHash() {
    return composedWorldStateStorage.get(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY).map(Bytes::wrap);
  }

  public Optional<Hash> getWorldStateBlockHash() {
    return composedWorldStateStorage
        .get(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY)
        .map(Bytes32::wrap)
        .map(Hash::wrap);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return getStorageValueByStorageSlotKey(
        () ->
            getAccount(accountHash)
                .map(
                    b ->
                        StateTrieAccountValue.readFrom(
                                org.hyperledger.besu.ethereum.rlp.RLP.input(b))
                            .getStorageRoot()),
        accountHash,
        storageSlotKey);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Hash>> storageRootSupplier,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    return flatDbStrategyProvider
        .getFlatDbStrategy(composedWorldStateStorage)
        .getFlatStorageValueByStorageSlotKey(
            this::getWorldStateRootHash,
            storageRootSupplier,
            (location, hash) -> getAccountStorageTrieNode(accountHash, location, hash),
            accountHash,
            storageSlotKey,
            composedWorldStateStorage);
  }

  @Override
  public NavigableMap<Bytes32, Bytes> streamFlatAccounts(
      final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return flatDbStrategyProvider
        .getFlatDbStrategy(composedWorldStateStorage)
        .streamAccountFlatDatabase(composedWorldStateStorage, startKeyHash, endKeyHash, max);
  }

  @Override
  public NavigableMap<Bytes32, Bytes> streamFlatAccounts(
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final Predicate<Pair<Bytes32, Bytes>> takeWhile) {
    return getFlatDbStrategy()
        .streamAccountFlatDatabase(composedWorldStateStorage, startKeyHash, endKeyHash, takeWhile);
  }

  @Override
  public NavigableMap<Bytes32, Bytes> streamFlatStorages(
      final Hash accountHash, final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return flatDbStrategyProvider
        .getFlatDbStrategy(composedWorldStateStorage)
        .streamStorageFlatDatabase(
            composedWorldStateStorage, accountHash, startKeyHash, endKeyHash, max);
  }

  @Override
  public NavigableMap<Bytes32, Bytes> streamFlatStorages(
      final Hash accountHash,
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final Predicate<Pair<Bytes32, Bytes>> takeWhile) {
    return getFlatDbStrategy()
        .streamStorageFlatDatabase(
            composedWorldStateStorage, accountHash, startKeyHash, endKeyHash, takeWhile);
  }

  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Hash addressHash, final Bytes32 startKeyHash, final int limit) {
    throw new RuntimeException("Bonsai Tries does not currently support enumerating storage");
  }

  @Override
  public Optional<Bytes> getNodeData(final Bytes location, final Bytes32 hash) {
    return Optional.empty();
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash, final Hash blockHash) {
    return composedWorldStateStorage
        .get(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY)
        .map(Bytes32::wrap)
        .map(hash -> hash.equals(rootHash) || trieLogStorage.containsKey(blockHash.toArrayUnsafe()))
        .orElse(false);
  }

  public void upgradeToFullFlatDbMode() {
    flatDbStrategyProvider.upgradeToFullFlatDbMode(composedWorldStateStorage);
  }

  public void downgradeToPartialFlatDbMode() {
    flatDbStrategyProvider.downgradeToPartialFlatDbMode(composedWorldStateStorage);
  }

  @Override
  public void clear() {
    subscribers.forEach(BonsaiStorageSubscriber::onClearStorage);
    flatDbStrategyProvider
        .getFlatDbStrategy(composedWorldStateStorage)
        .clearAll(composedWorldStateStorage);
    composedWorldStateStorage.clear(TRIE_BRANCH_STORAGE);
    trieLogStorage.clear();
    flatDbStrategyProvider.loadFlatDbStrategy(
        composedWorldStateStorage); // force reload of flat db reader strategy
  }

  @Override
  public void clearTrieLog() {
    subscribers.forEach(BonsaiStorageSubscriber::onClearTrieLog);
    trieLogStorage.clear();
  }

  @Override
  public void clearFlatDatabase() {
    subscribers.forEach(BonsaiStorageSubscriber::onClearFlatDatabaseStorage);
    flatDbStrategyProvider
        .getFlatDbStrategy(composedWorldStateStorage)
        .resetOnResync(composedWorldStateStorage);
  }

  @Override
  public BonsaiUpdater updater() {
    return new Updater(
        composedWorldStateStorage.startTransaction(),
        trieLogStorage.startTransaction(),
        flatDbStrategyProvider.getFlatDbStrategy(composedWorldStateStorage));
  }

  @Override
  public long prune(final Predicate<byte[]> inUseCheck) {
    throw new RuntimeException("Bonsai Tries do not work with pruning.");
  }

  public boolean pruneTrieLog(final Hash blockHash) {
    try {
      return trieLogStorage.tryDelete(blockHash.toArrayUnsafe());
    } catch (Exception e) {
      LOG.error("Error pruning trie log for block hash {}", blockHash, e);
      return false;
    }
  }

  @Override
  public long addNodeAddedListener(final NodesAddedListener listener) {
    throw new RuntimeException("addNodeAddedListener not available");
  }

  @Override
  public void removeNodeAddedListener(final long id) {
    throw new RuntimeException("removeNodeAddedListener not available");
  }

  @Override
  public FlatDbStrategy getFlatDbStrategy() {
    return flatDbStrategyProvider.getFlatDbStrategy(composedWorldStateStorage);
  }

  public interface BonsaiUpdater extends WorldStateStorage.Updater {
    BonsaiUpdater removeCode(final Hash accountHash);

    BonsaiUpdater removeAccountInfoState(final Hash accountHash);

    BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue);

    BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage);

    void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash);

    SegmentedKeyValueStorageTransaction getWorldStateTransaction();

    KeyValueStorageTransaction getTrieLogStorageTransaction();
  }

  public static class Updater implements BonsaiUpdater {

    private final SegmentedKeyValueStorageTransaction composedWorldStateTransaction;
    private final KeyValueStorageTransaction trieLogStorageTransaction;
    private final FlatDbStrategy flatDbStrategy;

    public Updater(
        final SegmentedKeyValueStorageTransaction composedWorldStateTransaction,
        final KeyValueStorageTransaction trieLogStorageTransaction,
        final FlatDbStrategy flatDbStrategy) {

      this.composedWorldStateTransaction = composedWorldStateTransaction;
      this.trieLogStorageTransaction = trieLogStorageTransaction;
      this.flatDbStrategy = flatDbStrategy;
    }

    @Override
    public BonsaiUpdater removeCode(final Hash accountHash) {
      flatDbStrategy.removeFlatCode(composedWorldStateTransaction, accountHash);
      return this;
    }

    @Override
    public BonsaiUpdater putCode(final Hash accountHash, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      flatDbStrategy.putFlatCode(composedWorldStateTransaction, accountHash, codeHash, code);
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountInfoState(final Hash accountHash) {
      flatDbStrategy.removeFlatAccount(composedWorldStateTransaction, accountHash);
      return this;
    }

    @Override
    public BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      flatDbStrategy.putFlatAccount(composedWorldStateTransaction, accountHash, accountValue);
      return this;
    }

    @Override
    public WorldStateStorage.Updater saveWorldState(
        final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
      composedWorldStateTransaction.remove(TRIE_BRANCH_STORAGE, location.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized BonsaiUpdater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE,
          Bytes.concatenate(accountHash, location).toArrayUnsafe(),
          node.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      flatDbStrategy.putFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash, storage);
      return this;
    }

    @Override
    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      flatDbStrategy.removeFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash);
    }

    @Override
    public SegmentedKeyValueStorageTransaction getWorldStateTransaction() {
      return composedWorldStateTransaction;
    }

    @Override
    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorageTransaction;
    }

    @Override
    public void commit() {
      // write the log ahead, then the worldstate
      trieLogStorageTransaction.commit();
      composedWorldStateTransaction.commit();
    }

    @Override
    public void rollback() {
      composedWorldStateTransaction.rollback();
      trieLogStorageTransaction.rollback();
    }
  }

  @Override
  public synchronized void close() throws Exception {
    // when the storage clears, close
    shouldClose.set(true);
    tryClose();
  }

  public synchronized long subscribe(final BonsaiStorageSubscriber sub) {
    if (isClosed.get()) {
      throw new RuntimeException("Storage is marked to close or has already closed");
    }
    return subscribers.subscribe(sub);
  }

  public synchronized void unSubscribe(final long id) {
    subscribers.unsubscribe(id);
    try {
      tryClose();
    } catch (Exception e) {
      LOG.atWarn()
          .setMessage("exception while trying to close : {}")
          .addArgument(e::getMessage)
          .log();
    }
  }

  protected synchronized void tryClose() throws Exception {
    if (shouldClose.get() && subscribers.getSubscriberCount() < 1) {
      doClose();
    }
  }

  protected synchronized void doClose() throws Exception {
    if (!isClosed.get()) {
      // alert any subscribers we are closing:
      subscribers.forEach(BonsaiStorageSubscriber::onCloseStorage);

      // close all of the KeyValueStorages:
      composedWorldStateStorage.close();
      trieLogStorage.close();

      // set storage closed
      isClosed.set(true);
    }
  }

  public interface BonsaiStorageSubscriber {
    default void onClearStorage() {}

    default void onClearFlatDatabaseStorage() {}

    default void onClearTrieLog() {}

    default void onCloseStorage() {}
  }
}
