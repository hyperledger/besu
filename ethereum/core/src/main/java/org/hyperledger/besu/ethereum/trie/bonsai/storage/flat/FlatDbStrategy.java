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
package org.hyperledger.besu.ethereum.trie.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

/**
 * This class represents a FlatDbReaderStrategy, which is responsible for reading and writing data
 * from flat databases. It implements various methods for storing and retrieving account data, code
 * data, and storage data from the corresponding KeyValueStorage.
 */
public abstract class FlatDbStrategy {

  protected final MetricsSystem metricsSystem;
  protected final Counter getAccountCounter;
  protected final Counter getAccountFoundInFlatDatabaseCounter;

  protected final Counter getStorageValueCounter;
  protected final Counter getStorageValueFlatDatabaseCounter;
  private final CodeStorageStrategy codeStorageStrategy;

  public FlatDbStrategy(
      final MetricsSystem metricsSystem,
      final boolean useAccountHashCodeStorage,
      final boolean deleteCodeInCodeHashStorageMode) {
    this.metricsSystem = metricsSystem;
    this.codeStorageStrategy =
        useAccountHashCodeStorage
            ? new AccountHashCodeStorageStrategy()
            : new CodeHashCodeStorageStrategy(deleteCodeInCodeHashStorageMode);

    getAccountCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_total",
            "Total number of calls to getAccount");

    getAccountFoundInFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_flat_database",
            "Number of accounts found in the flat database");

    getStorageValueCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_total",
            "Total number of calls to getStorageValueBySlotHash");

    getStorageValueFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_flat_database",
            "Number of storage slots found in the flat database");
  }

  /*
   * Retrieves the account data for the given account hash, using the world state root hash supplier and node loader.
   */
  public abstract Optional<Bytes> getFlatAccount(
      Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      NodeLoader nodeLoader,
      Hash accountHash,
      SegmentedKeyValueStorage storage);

  /*
   * Retrieves the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */

  public abstract Optional<Bytes> getFlatStorageValueByStorageSlotKey(
      Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      Supplier<Optional<Hash>> storageRootSupplier,
      NodeLoader nodeLoader,
      Hash accountHash,
      StorageSlotKey storageSlotKey,
      SegmentedKeyValueStorage storageStorage);

  /*
   * Retrieves the code data for the given code hash and account hash.
   */
  public Optional<Bytes> getFlatCode(
      final Hash codeHash, final Hash accountHash, final SegmentedKeyValueStorage storage) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return codeStorageStrategy.getFlatCode(codeHash, accountHash, storage);
    }
  }

  /*
   * Puts the account data for the given account hash, using the world state root hash supplier and node loader.
   */
  public void putFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes accountValue) {
    transaction.put(ACCOUNT_INFO_STATE, accountHash.toArrayUnsafe(), accountValue.toArrayUnsafe());
  }

  public void removeFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {
    transaction.remove(ACCOUNT_INFO_STATE, accountHash.toArrayUnsafe());
  }

  /*
   * Puts the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  public void putFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash,
      final Bytes storage) {
    transaction.put(
        ACCOUNT_STORAGE_STORAGE,
        Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(),
        storage.toArrayUnsafe());
  }

  /*
   * Removes the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  public void removeFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash) {
    transaction.remove(
        ACCOUNT_STORAGE_STORAGE, Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
  }

  /*
   * Removes code for the given account hash.
   */
  public void removeFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash) {
    codeStorageStrategy.removeFlatCode(transaction, accountHash, codeHash);
  }

  /*
   * Puts the code data for the given code hash and account hash.
   */
  public void putFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash,
      final Bytes code) {
    codeStorageStrategy.putFlatCode(transaction, accountHash, codeHash, code);
  }

  public void clearAll(final SegmentedKeyValueStorage storage) {
    storage.clear(ACCOUNT_INFO_STATE);
    storage.clear(ACCOUNT_STORAGE_STORAGE);
    storage.clear(CODE_STORAGE);
  }

  public void resetOnResync(final SegmentedKeyValueStorage storage) {
    storage.clear(ACCOUNT_INFO_STATE);
    storage.clear(ACCOUNT_STORAGE_STORAGE);
  }

  public Map<Bytes32, Bytes> streamAccountFlatDatabase(
      final SegmentedKeyValueStorage storage,
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final long max) {
    final Stream<Pair<Bytes32, Bytes>> pairStream =
        storage
            .streamFromKey(
                ACCOUNT_INFO_STATE, startKeyHash.toArrayUnsafe(), endKeyHash.toArrayUnsafe())
            .limit(max)
            .map(pair -> new Pair<>(Bytes32.wrap(pair.getKey()), Bytes.wrap(pair.getValue())));

    final TreeMap<Bytes32, Bytes> collected =
        pairStream.collect(
            Collectors.toMap(Pair::getFirst, Pair::getSecond, (v1, v2) -> v1, TreeMap::new));
    pairStream.close();
    return collected;
  }

  public Map<Bytes32, Bytes> streamStorageFlatDatabase(
      final SegmentedKeyValueStorage storage,
      final Hash accountHash,
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final long max) {
    final Stream<Pair<Bytes32, Bytes>> pairStream =
        storage
            .streamFromKey(
                ACCOUNT_STORAGE_STORAGE,
                Bytes.concatenate(accountHash, startKeyHash).toArrayUnsafe(),
                Bytes.concatenate(accountHash, endKeyHash).toArrayUnsafe())
            .limit(max)
            .map(
                pair ->
                    new Pair<>(
                        Bytes32.wrap(Bytes.wrap(pair.getKey()).slice(Hash.SIZE)),
                        RLP.encodeValue(Bytes.wrap(pair.getValue()).trimLeadingZeros())));

    final TreeMap<Bytes32, Bytes> collected =
        pairStream.collect(
            Collectors.toMap(Pair::getFirst, Pair::getSecond, (v1, v2) -> v1, TreeMap::new));
    pairStream.close();
    return collected;
  }
}
