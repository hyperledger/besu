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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
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

  public BonsaiSnapshotWorldStateKeyValueStorage(final StorageProvider snappableStorageProvider) {
    this(
        snappableStorageProvider
            .getSnappableStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE)
            .takeSnapshot(),
        snappableStorageProvider
            .getSnappableStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE)
            .takeSnapshot(),
        snappableStorageProvider
            .getSnappableStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE)
            .takeSnapshot(),
        snappableStorageProvider
            .getSnappableStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE)
            .takeSnapshot(),
        snappableStorageProvider.getStorageBySegmentIdentifier(
            KeyValueSegmentIdentifier.TRIE_LOG_STORAGE));
  }

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
  public synchronized long subscribe(final BonsaiStorageSubscriber sub) {
    if (shouldClose.get()) {
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
      warnLambda(LOG, "exception while trying to close : {}", e::getMessage);
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

    private final SnappedKeyValueStorage accountStorage;
    private final SnappedKeyValueStorage codeStorage;
    private final SnappedKeyValueStorage storageStorage;
    private final SnappedKeyValueStorage trieBranchStorage;
    private final KeyValueStorageTransaction trieLogStorageTransaction;

    public SnapshotUpdater(
        final SnappedKeyValueStorage accountStorage,
        final SnappedKeyValueStorage codeStorage,
        final SnappedKeyValueStorage storageStorage,
        final SnappedKeyValueStorage trieBranchStorage,
        final KeyValueStorage trieLogStorage) {
      this.accountStorage = accountStorage;
      this.codeStorage = codeStorage;
      this.storageStorage = storageStorage;
      this.trieBranchStorage = trieBranchStorage;
      this.trieLogStorageTransaction = trieLogStorage.startTransaction();
    }

    @Override
    public BonsaiUpdater removeCode(final Hash accountHash) {
      codeStorage.getSnapshotTransaction().remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putCode(
        final Hash accountHash, final Bytes32 nodeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      codeStorage.getSnapshotTransaction().put(accountHash.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountInfoState(final Hash accountHash) {
      accountStorage.getSnapshotTransaction().remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      accountStorage
          .getSnapshotTransaction()
          .put(accountHash.toArrayUnsafe(), accountValue.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      storageStorage
          .getSnapshotTransaction()
          .put(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(), storage.toArrayUnsafe());
      return this;
    }

    @Override
    public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
      storageStorage
          .getSnapshotTransaction()
          .remove(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
    }

    @Override
    public KeyValueStorageTransaction getTrieBranchStorageTransaction() {
      return trieBranchStorage.getSnapshotTransaction();
    }

    @Override
    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorageTransaction;
    }

    @Override
    public WorldStateStorage.Updater saveWorldState(
        final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      trieBranchStorage
          .getSnapshotTransaction()
          .put(Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      trieBranchStorage.getSnapshotTransaction().put(WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      trieBranchStorage
          .getSnapshotTransaction()
          .put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorage
          .getSnapshotTransaction()
          .put(location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater removeAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash) {
      trieBranchStorage.getSnapshotTransaction().remove(location.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorage
          .getSnapshotTransaction()
          .put(Bytes.concatenate(accountHash, location).toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public void commit() {
      // only commit the trielog layer transaction, leave the snapshot transactions open:
      trieLogStorageTransaction.commit();
    }

    @Override
    public void rollback() {
      // no-op
    }
  }
}
