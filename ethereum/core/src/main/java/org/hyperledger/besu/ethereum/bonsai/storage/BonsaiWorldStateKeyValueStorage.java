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
package org.hyperledger.besu.ethereum.bonsai.storage;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.storage.flat.FlatDbReaderStrategy;
import org.hyperledger.besu.ethereum.bonsai.storage.flat.FullFlatDbReaderStrategy;
import org.hyperledger.besu.ethereum.bonsai.storage.flat.PartialFlatDbReaderStrategy;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.GlobalKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.Subscribers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class BonsaiWorldStateKeyValueStorage implements WorldStateStorage, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiWorldStateKeyValueStorage.class);

  // 0x776f726c64526f6f74
  public static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);
  // 0x776f726c64426c6f636b48617368
  public static final byte[] WORLD_BLOCK_HASH_KEY =
      "worldBlockHash".getBytes(StandardCharsets.UTF_8);

  // 0x666C61744462537461747573
  public static final byte[] FLAT_DB_MODE = "flatDbStatus".getBytes(StandardCharsets.UTF_8);

  protected FlatDbMode flatDbMode;
  protected FlatDbReaderStrategy flatDbReaderStrategy;

  protected final KeyValueStorage accountStorage;
  protected final KeyValueStorage codeStorage;
  protected final KeyValueStorage storageStorage;
  protected final KeyValueStorage trieBranchStorage;
  protected final KeyValueStorage trieLogStorage;

  protected final StorageProvider provider;

  protected final ObservableMetricsSystem metricsSystem;

  private final AtomicBoolean shouldClose = new AtomicBoolean(false);

  protected final AtomicBoolean isClosed = new AtomicBoolean(false);

  protected final Subscribers<BonsaiStorageSubscriber> subscribers = Subscribers.create();

  public BonsaiWorldStateKeyValueStorage(
      final StorageProvider provider, final ObservableMetricsSystem metricsSystem) {
    this.accountStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    this.codeStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    this.storageStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    this.trieBranchStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    this.trieLogStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
    this.provider = provider;
    this.metricsSystem = metricsSystem;
    loadFlatDbStrategy();
  }

  public BonsaiWorldStateKeyValueStorage(
      final StorageProvider provider,
      final FlatDbMode flatDbMode,
      final FlatDbReaderStrategy flatDbReaderStrategy,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage,
      final ObservableMetricsSystem metricsSystem) {
    this.provider = provider;
    this.flatDbMode = flatDbMode;
    this.flatDbReaderStrategy = flatDbReaderStrategy;
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
    this.metricsSystem = metricsSystem;
  }

  public void loadFlatDbStrategy() {
    this.flatDbMode =
        FlatDbMode.fromVersion(
            trieBranchStorage
                .get(FLAT_DB_MODE)
                .map(Bytes::wrap)
                .orElse(
                    FlatDbMode.PARTIAL
                        .getVersion())); // for backward compatibility we use partial as
    // default
    LOG.info("Bonsai flat db mode found {}", flatDbMode);
    if (flatDbMode == FlatDbMode.FULL) {
      this.flatDbReaderStrategy = new FullFlatDbReaderStrategy(metricsSystem);
    } else {
      this.flatDbReaderStrategy = new PartialFlatDbReaderStrategy(metricsSystem);
    }
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.BONSAI;
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return flatDbMode;
  }

  public FlatDbReaderStrategy getFlatDbReaderStrategy() {
    return flatDbReaderStrategy;
  }

  @Override
  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return getFlatDbReaderStrategy().getCode(codeHash, accountHash, codeStorage);
    }
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getFlatDbReaderStrategy()
        .getAccount(
            this::getWorldStateRootHash,
            this::getAccountStateTrieNode,
            accountHash,
            accountStorage);
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage
          .get(location.toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(nodeHash));
    }
  }

  /**
   * Retrieves the storage trie node associated with the specified account and location, if
   * available.
   *
   * @param accountHash The hash of the account.
   * @param location The location within the storage trie.
   * @param maybeNodeHash The optional hash of the storage trie node to validate the retrieved data
   *     against.
   * @return The optional bytes of the storage trie node.
   */
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Optional<Bytes32> maybeNodeHash) {
    if (maybeNodeHash.filter(hash -> hash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)).isPresent()) {
      return Optional.of(MerkleTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage
          .get(Bytes.concatenate(accountHash, location).toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(data -> maybeNodeHash.map(hash -> Hash.hash(data).equals(hash)).orElse(true));
    }
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    return getAccountStorageTrieNode(accountHash, location, Optional.ofNullable(nodeHash));
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

  public Optional<Hash> getWorldStateBlockHash() {
    return trieBranchStorage.get(WORLD_BLOCK_HASH_KEY).map(Bytes32::wrap).map(Hash::wrap);
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
    return getFlatDbReaderStrategy()
        .getStorageValueByStorageSlotKey(
            this::getWorldStateRootHash,
            storageRootSupplier,
            (location, hash) -> getAccountStorageTrieNode(accountHash, location, hash),
            accountHash,
            storageSlotKey,
            storageStorage);
  }

  @Override
  public Map<Bytes32, Bytes> streamFlatAccounts(
      final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return getFlatDbReaderStrategy()
        .streamAccountFlatDatabase(accountStorage, startKeyHash, endKeyHash, max);
  }

  @Override
  public Map<Bytes32, Bytes> streamFlatStorages(
      final Hash accountHash, final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    return getFlatDbReaderStrategy()
        .streamStorageFlatDatabase(storageStorage, accountHash, startKeyHash, endKeyHash, max);
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
        .map(hash -> hash.equals(rootHash) || trieLogStorage.containsKey(blockHash.toArrayUnsafe()))
        .orElse(false);
  }

  public void upgradeToFullFlatDbMode() {
    final KeyValueStorageTransaction transaction = trieBranchStorage.startTransaction();
    transaction.put(FLAT_DB_MODE, FlatDbMode.FULL.getVersion().toArrayUnsafe());
    transaction.commit();
    loadFlatDbStrategy(); // force reload of flat db reader strategy
  }

  public void downgradeToPartialFlatDbMode() {
    final KeyValueStorageTransaction transaction = trieBranchStorage.startTransaction();
    transaction.put(FLAT_DB_MODE, FlatDbMode.PARTIAL.getVersion().toArrayUnsafe());
    transaction.commit();
    loadFlatDbStrategy(); // force reload of flat db reader strategy
  }

  @Override
  public void clear() {
    subscribers.forEach(BonsaiStorageSubscriber::onClearStorage);
    getFlatDbReaderStrategy().clearAll(accountStorage, storageStorage, codeStorage);
    trieBranchStorage.clear();
    trieLogStorage.clear();
    loadFlatDbStrategy(); // force reload of flat db reader strategy
  }

  @Override
  public void clearTrieLog() {
    subscribers.forEach(BonsaiStorageSubscriber::onClearTrieLog);
    trieLogStorage.clear();
  }

  @Override
  public void clearFlatDatabase() {
    subscribers.forEach(BonsaiStorageSubscriber::onClearFlatDatabaseStorage);
    getFlatDbReaderStrategy().resetOnResync(accountStorage, storageStorage);
  }

  @Override
  public BonsaiUpdater updater() {
    return new Updater(
        provider.createGlobalKeyValueStorageTransaction(),
        accountStorage,
        codeStorage,
        storageStorage,
        trieBranchStorage,
        trieLogStorage);
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

    private final GlobalKeyValueStorageTransaction<?> globalTransaction;
    private final KeyValueStorageTransaction accountStorageSet;
    private final KeyValueStorageTransaction codeStorageSet;
    private final KeyValueStorageTransaction storageStorageSet;
    private final KeyValueStorageTransaction trieBranchStorageSet;
    private final KeyValueStorageTransaction trieLogStorageSet;

    public Updater(
        final GlobalKeyValueStorageTransaction<?> globalTransaction,
        final KeyValueStorage accountStorage,
        final KeyValueStorage codeStorage,
        final KeyValueStorage storageStorage,
        final KeyValueStorage trieBranchStorage,
        final KeyValueStorage trieLogStorage) {
      this.globalTransaction = globalTransaction;
      this.accountStorageSet = globalTransaction.includeInGlobalTransactionStorage(accountStorage);
      this.codeStorageSet = globalTransaction.includeInGlobalTransactionStorage(codeStorage);
      this.storageStorageSet = globalTransaction.includeInGlobalTransactionStorage(storageStorage);
      this.trieBranchStorageSet =
          globalTransaction.includeInGlobalTransactionStorage(trieBranchStorage);
      this.trieLogStorageSet = globalTransaction.includeInGlobalTransactionStorage(trieLogStorage);
    }

    @Override
    public BonsaiUpdater removeCode(final Hash accountHash) {
      codeStorageSet.remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putCode(final Hash accountHash, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      codeStorageSet.put(accountHash.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountInfoState(final Hash accountHash) {
      accountStorageSet.remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      accountStorageSet.put(accountHash.toArrayUnsafe(), accountValue.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater saveWorldState(
        final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      trieBranchStorageSet.put(Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      trieBranchStorageSet.put(WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      trieBranchStorageSet.put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageSet.put(location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
      trieBranchStorageSet.remove(location.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized BonsaiUpdater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageSet.put(
          Bytes.concatenate(accountHash, location).toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      storageStorageSet.put(
          Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(), storage.toArrayUnsafe());
      return this;
    }

    @Override
    public synchronized void removeStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash) {
      storageStorageSet.remove(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
    }

    @Override
    public KeyValueStorageTransaction getTrieBranchStorageTransaction() {
      return trieBranchStorageSet;
    }

    @Override
    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorageSet;
    }

    @Override
    public void commit() {
      globalTransaction.commit();
    }

    @Override
    public void rollback() {
      globalTransaction.rollback();
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
      accountStorage.close();
      codeStorage.close();
      storageStorage.close();
      trieBranchStorage.close();
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
