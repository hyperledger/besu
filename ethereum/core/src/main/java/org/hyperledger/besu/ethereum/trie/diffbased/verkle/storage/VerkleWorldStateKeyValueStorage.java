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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat.FullFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VerkleWorldStateKeyValueStorage extends DiffBasedWorldStateKeyValueStorage
    implements WorldStateKeyValueStorage {

  protected FullFlatDbStrategy flatDbStrategy;

  public VerkleWorldStateKeyValueStorage(
          final StorageProvider provider,
          final MetricsSystem metricsSystem) {
    super(
        provider.getStorageBySegmentIdentifiers(
            List.of(
                ACCOUNT_INFO_STATE, CODE_STORAGE, ACCOUNT_STORAGE_STORAGE, TRIE_BRANCH_STORAGE)),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE));
    this.flatDbStrategy = new FullFlatDbStrategy(metricsSystem);
  }

  public VerkleWorldStateKeyValueStorage(
          final FlatDbStrategy flatDbStrategy,
      final SegmentedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage) {
    super(composedWorldStateStorage, trieLogStorage);
  }

  @Override
  public FlatDbStrategy getFlatDbStrategy() {
    return flatDbStrategy;
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.VERKLE;
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return FlatDbMode.FULL;
  }

  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return getFlatDbStrategy().getFlatCode(codeHash, accountHash, composedWorldStateStorage);
    }
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getFlatDbStrategy().getFlatAccount(null, null, accountHash, composedWorldStateStorage);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return getFlatDbStrategy()
        .getFlatStorageValueByStorageSlotKey(
            null, null, null, accountHash, storageSlotKey, composedWorldStateStorage);
  }

  @Override
  public void clear() {
    super.clear();
  }

  @Override
  public Updater updater() {
    return new Updater(
        composedWorldStateStorage.startTransaction(),
        trieLogStorage.startTransaction(),
        flatDbStrategy);
  }

  public static class Updater implements DiffBasedWorldStateKeyValueStorage.Updater {

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

    public Updater removeCode(final Hash accountHash) {
      flatDbStrategy.removeFlatCode(composedWorldStateTransaction, accountHash);
      return this;
    }

    public Updater putCode(final Hash accountHash, final Bytes code) {
      // Skip the hash calculation for empty code
      final Hash codeHash = code.size() == 0 ? Hash.EMPTY : Hash.hash(code);
      return putCode(accountHash, codeHash, code);
    }

    public Updater putCode(final Hash accountHash, final Bytes32 codeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      flatDbStrategy.putFlatCode(composedWorldStateTransaction, accountHash, codeHash, code);
      return this;
    }

    public Updater removeAccountInfoState(final Hash accountHash) {
      flatDbStrategy.removeFlatAccount(composedWorldStateTransaction, accountHash);
      return this;
    }

    public Updater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      flatDbStrategy.putFlatAccount(composedWorldStateTransaction, accountHash, accountValue);
      return this;
    }

    @Override
    public Updater saveWorldState(final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    public Updater putStateTrieNode(final Bytes location, final Bytes node) {
      composedWorldStateTransaction.put(
          TRIE_BRANCH_STORAGE, location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    public Updater removeStateTrieNode(final Bytes location) {
      composedWorldStateTransaction.remove(TRIE_BRANCH_STORAGE, location.toArrayUnsafe());
      return this;
    }

    public synchronized Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      flatDbStrategy.putFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash, storage);
      return this;
    }

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
}
