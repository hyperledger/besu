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
package org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
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
  protected final CodeStorageStrategy codeStorageStrategy;

  public FlatDbStrategy(
      final MetricsSystem metricsSystem, final CodeStorageStrategy codeStorageStrategy) {
    this.metricsSystem = metricsSystem;
    this.codeStorageStrategy = codeStorageStrategy;

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


  public boolean isCodeByCodeHash() {
    return codeStorageStrategy instanceof CodeHashCodeStorageStrategy;
  }

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

  public abstract void clearAll(final SegmentedKeyValueStorage storage) ;

  public abstract void resetOnResync(final SegmentedKeyValueStorage storage);
}
