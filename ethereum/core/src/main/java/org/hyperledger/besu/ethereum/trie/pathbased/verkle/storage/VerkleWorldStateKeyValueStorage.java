/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.cache.preloader.StemPreloader;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.flat.VerkleFlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.flat.VerkleLegacyFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.flat.VerkleStemFlatDbStrategy;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleWorldStateKeyValueStorage extends PathBasedWorldStateKeyValueStorage
    implements WorldStateKeyValueStorage {

  protected final FlatDbStrategyProvider flatDbStrategyProvider;
  protected final StemPreloader stemPreloader;
  protected MetricsSystem metricsSystem;
  protected final DataStorageConfiguration dataStorageConfiguration;

  public VerkleWorldStateKeyValueStorage(
      final StorageProvider provider,
      final StemPreloader stemPreloader,
      final DataStorageConfiguration dataStorageConfiguration,
      final MetricsSystem metricsSystem) {
    super(
        provider.getStorageBySegmentIdentifiers(List.of(CODE_STORAGE, TRIE_BRANCH_STORAGE)),
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE));
    this.stemPreloader = stemPreloader;
    this.metricsSystem = metricsSystem;
    this.dataStorageConfiguration = dataStorageConfiguration;
    this.flatDbStrategyProvider =
        new VerkleFlatDbStrategyProvider(
            metricsSystem, dataStorageConfiguration, composedWorldStateStorage);
  }

  public VerkleWorldStateKeyValueStorage(
      final SegmentedKeyValueStorage composedWorldStateStorage,
      final KeyValueStorage trieLogStorage,
      final StemPreloader stemPreloader,
      final DataStorageConfiguration dataStorageConfiguration,
      final MetricsSystem metricsSystem) {
    super(composedWorldStateStorage, trieLogStorage);
    this.metricsSystem = metricsSystem;
    this.dataStorageConfiguration = dataStorageConfiguration;
    this.flatDbStrategyProvider =
        new VerkleFlatDbStrategyProvider(
            metricsSystem, dataStorageConfiguration, composedWorldStateStorage);
    this.stemPreloader = stemPreloader;
  }

  @Override
  public FlatDbStrategy getFlatDbStrategy() {
    return flatDbStrategyProvider.getFlatDbStrategy();
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.VERKLE;
  }

  @Override
  public FlatDbMode getFlatDbMode() {
    return flatDbStrategyProvider.getFlatDbMode();
  }

  public StemPreloader getStemPreloader() {
    return stemPreloader;
  }

  public Optional<Bytes> getCode(final Hash codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return getFlatDbStrategy().getFlatCode(codeHash, accountHash, composedWorldStateStorage);
    }
  }

  public Optional<VerkleAccount> getAccount(
      final Address address, final PathBasedWorldView context) {
    final FlatDbStrategy flatDbStrategy = getFlatDbStrategy();
    // TODO this code is not 100% clean but legacy flat db will be removed in the future
    if (flatDbStrategy instanceof VerkleLegacyFlatDbStrategy legacyFlatDbStrategy) {
      return legacyFlatDbStrategy.getFlatAccount(address, context, composedWorldStateStorage);
    } else if (flatDbStrategy instanceof VerkleStemFlatDbStrategy stemFlatDbStrategy) {
      return stemFlatDbStrategy.getFlatAccount(
          address, context, stemPreloader, composedWorldStateStorage);
    }
    return Optional.empty();
  }

  public Optional<UInt256> getStorageValueByStorageSlotKey(
      final Address address, final StorageSlotKey storageSlotKey) {
    final FlatDbStrategy flatDbStrategy = getFlatDbStrategy();
    // TODO this code is not 100% clean but legacy flat db will be removed in the future
    if (flatDbStrategy instanceof VerkleLegacyFlatDbStrategy legacyFlatDbStrategy) {
      return legacyFlatDbStrategy
          .getFlatStorageValueByStorageSlotKey(address, storageSlotKey, composedWorldStateStorage)
          .map(UInt256::fromBytes);
    } else if (flatDbStrategy instanceof VerkleStemFlatDbStrategy stemFlatDbStrategy) {
      return stemFlatDbStrategy
          .getFlatStorageValueByStorageSlotKey(
              address, storageSlotKey, stemPreloader, composedWorldStateStorage)
          .map(UInt256::fromBytes);
    }
    return Optional.empty();
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
        getFlatDbStrategy());
  }

  public static class Updater implements PathBasedWorldStateKeyValueStorage.Updater {

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

    public Updater removeCode(final Hash accountHash, final Hash codeHash) {
      flatDbStrategy.removeFlatCode(composedWorldStateTransaction, accountHash, codeHash);
      return this;
    }

    public Updater putCode(final Hash accountHash, final Bytes code) {
      // Skip the hash calculation for empty code
      final Hash codeHash = code.size() == 0 ? Hash.EMPTY : Hash.hash(code);
      return putCode(accountHash, codeHash, code);
    }

    public Updater putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
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

    public Updater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      flatDbStrategy.putFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash, storage);
      return this;
    }

    public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
      flatDbStrategy.removeFlatAccountStorageValueByStorageSlotHash(
          composedWorldStateTransaction, accountHash, slotHash);
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
