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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiSnapshotWorldStateKeyValueStorage extends BonsaiWorldStateKeyValueStorage
    implements BonsaiStorageSubscriber {

  private static final Logger LOG =
      LoggerFactory.getLogger(BonsaiSnapshotWorldStateKeyValueStorage.class);

  private final AtomicReference<BonsaiWorldStateKeyValueStorage> parentStorage =
      new AtomicReference<>();
  private final AtomicLong parentStorageSubscriberId = new AtomicLong(Long.MAX_VALUE);
  private final AtomicBoolean shouldClose = new AtomicBoolean(false);
  private final AtomicBoolean isClosed = new AtomicBoolean(false);

  public BonsaiSnapshotWorldStateKeyValueStorage(
      final SnappedKeyValueStorage accountStorage,
      final SnappedKeyValueStorage codeStorage,
      final SnappedKeyValueStorage storageStorage,
      final SnappedKeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    super(accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);
  }

  @Override
  public BonsaiUpdater updater() {
    return new SnapshotUpdater(
        (SnappedKeyValueStorage) accountStorage,
        (SnappedKeyValueStorage) codeStorage,
        (SnappedKeyValueStorage) storageStorage,
        (SnappedKeyValueStorage) trieBranchStorage,
        trieLogStorage);
  }

  @Override
  public void clear() {
    // snapshot storage does not implement clear
    throw new StorageException("Snapshot storage does not implement clear");
  }

  @Override
  public void clearFlatDatabase() {
    // snapshot storage does not implement clear
    throw new StorageException("Snapshot storage does not implement clear");
  }

  @Override
  public void clearTrieLog() {
    // snapshot storage does not implement clear
    throw new StorageException("Snapshot storage does not implement clear");
  }

  @Override
  public synchronized long subscribe(final BonsaiStorageSubscriber sub) {
    if (isClosed.get()) {
      throw new RuntimeException("Storage is marked to close or has already closed");
    }
    return super.subscribe(sub);
  }

  @Override
  public synchronized void unSubscribe(final long id) {
    super.unSubscribe(id);
    try {
      tryClose();
    } catch (Exception e) {
      LOG.atWarn()
          .setMessage("exception while trying to close : {}")
          .addArgument(e::getMessage)
          .log();
    }
  }

  void subscribeToParentStorage(final BonsaiWorldStateKeyValueStorage parentStorage) {
    this.parentStorage.set(parentStorage);
    parentStorageSubscriberId.set(parentStorage.subscribe(this));
  }

  @Override
  public void onClearStorage() {
    try {
      // when the parent storage clears, close regardless of subscribers
      doClose();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void onClearFlatDatabaseStorage() {
    // when the parent storage clears, close regardless of subscribers
    try {
      doClose();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void onClearTrieLog() {
    // when the parent storage clears, close regardless of subscribers
    try {
      doClose();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void close() throws Exception {
    // when the parent storage clears, close
    shouldClose.set(true);
    tryClose();
  }

  protected synchronized void tryClose() throws Exception {
    if (shouldClose.get() && subscribers.getSubscriberCount() < 1) {
      // attempting to close already closed snapshots will segfault
      doClose();
    }
  }

  private void doClose() throws Exception {
    if (!isClosed.get()) {
      // alert any subscribers we are closing:
      subscribers.forEach(BonsaiStorageSubscriber::onCloseStorage);

      // unsubscribe from parent storage if we have subscribed
      Optional.ofNullable(parentStorage.get())
          .filter(__ -> parentStorageSubscriberId.get() != Long.MAX_VALUE)
          .ifPresent(parent -> parent.unSubscribe(parentStorageSubscriberId.get()));

      // close all of the SnappedKeyValueStorages:
      accountStorage.close();
      codeStorage.close();
      storageStorage.close();
      trieBranchStorage.close();

      // set storage closed
      isClosed.set(true);
    }
  }

  public static class SnapshotUpdater implements BonsaiWorldStateKeyValueStorage.BonsaiUpdater {

    private final KeyValueStorageTransaction accountStorageTransaction;
    private final KeyValueStorageTransaction codeStorageTransaction;
    private final KeyValueStorageTransaction storageStorageTransaction;
    private final KeyValueStorageTransaction trieBranchStorageTransaction;
    private final KeyValueStorageTransaction trieLogStorageTransaction;

    public SnapshotUpdater(
        final SnappedKeyValueStorage accountStorage,
        final SnappedKeyValueStorage codeStorage,
        final SnappedKeyValueStorage storageStorage,
        final SnappedKeyValueStorage trieBranchStorage,
        final KeyValueStorage trieLogStorage) {
      this.accountStorageTransaction = accountStorage.getSnapshotTransaction();
      this.codeStorageTransaction = codeStorage.getSnapshotTransaction();
      this.storageStorageTransaction = storageStorage.getSnapshotTransaction();
      this.trieBranchStorageTransaction = trieBranchStorage.getSnapshotTransaction();
      this.trieLogStorageTransaction = trieLogStorage.startTransaction();
    }

    @Override
    public BonsaiUpdater removeCode(final Hash accountHash) {
      codeStorageTransaction.remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putCode(
        final Hash accountHash, final Bytes32 nodeHash, final Bytes code) {
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
    public BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      storageStorageTransaction.put(
          Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(), storage.toArrayUnsafe());
      return this;
    }

    @Override
    public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
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
    public WorldStateStorage.Updater saveWorldState(
        final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      trieBranchStorageTransaction.put(Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      trieBranchStorageTransaction.put(WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      trieBranchStorageTransaction.put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorageTransaction.put(location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater removeAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash) {
      trieBranchStorageTransaction.remove(location.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putAccountStorageTrieNode(
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
    public void commit() {
      accountStorageTransaction.commit();
      codeStorageTransaction.commit();
      storageStorageTransaction.commit();
      trieBranchStorageTransaction.commit();
      trieLogStorageTransaction.commit();
    }

    @Override
    public void rollback() {
      // no-op
    }
  }
}
