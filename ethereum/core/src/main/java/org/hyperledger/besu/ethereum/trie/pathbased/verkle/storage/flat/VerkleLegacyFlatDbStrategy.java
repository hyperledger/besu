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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Strategy for managing Verkle accounts in the flat database as before Verkle was introduced.
 *
 * <p>This strategy handles accounts, slots, and code similar to the legacy approach, ensuring
 * consistency and compatibility with the pre-Verkle structure.
 */
public class VerkleLegacyFlatDbStrategy extends FlatDbStrategy {

  protected final Counter getAccountNotFoundInFlatDatabaseCounter;

  protected final Counter getStorageValueNotFoundInFlatDatabaseCounter;

  public VerkleLegacyFlatDbStrategy(
      final MetricsSystem metricsSystem, final CodeStorageStrategy codeStorageStrategy) {
    super(metricsSystem, codeStorageStrategy);

    getAccountNotFoundInFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_missing_flat_database",
            "Number of accounts not found in the flat database");

    getStorageValueNotFoundInFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_missing_flat_database",
            "Number of storage slots not found in the flat database");
  }

  public Optional<VerkleAccount> getFlatAccount(
      final Address address,
      final PathBasedWorldView context,
      final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();
    final Optional<VerkleAccount> accountFound =
        storage
            .get(ACCOUNT_INFO_STATE, address.addressHash().toArrayUnsafe())
            .map(Bytes::wrap)
            .map(encoded -> VerkleAccount.fromLegacyFormat(context, address, encoded, true));
    if (accountFound.isPresent()) {
      getAccountFoundInFlatDatabaseCounter.inc();
    } else {
      getAccountNotFoundInFlatDatabaseCounter.inc();
    }
    return accountFound;
  }

  public Optional<Bytes> getFlatStorageValueByStorageSlotKey(
      final Address address,
      final StorageSlotKey storageSlotKey,
      final SegmentedKeyValueStorage storage) {
    getStorageValueCounter.inc();
    final Optional<Bytes> storageFound =
        storage
            .get(
                ACCOUNT_STORAGE_STORAGE,
                Bytes.concatenate(address.addressHash(), storageSlotKey.getSlotHash())
                    .toArrayUnsafe())
            .map(Bytes::wrap);
    if (storageFound.isPresent()) {
      getStorageValueFlatDatabaseCounter.inc();
    } else {
      getStorageValueNotFoundInFlatDatabaseCounter.inc();
    }

    return storageFound;
  }

  @Override
  public void putFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes accountValue) {
    transaction.put(ACCOUNT_INFO_STATE, accountHash.toArrayUnsafe(), accountValue.toArrayUnsafe());
  }

  @Override
  public void removeFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {
    transaction.remove(ACCOUNT_INFO_STATE, accountHash.toArrayUnsafe());
  }

  @Override
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

  @Override
  public void removeFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash) {
    transaction.remove(
        ACCOUNT_STORAGE_STORAGE, Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
  }

  @Override
  public void clearAll(final SegmentedKeyValueStorage storage) {
    // NOOP
    // we cannot clear flatdb in verkle as we are using directly the trie
  }

  @Override
  public void resetOnResync(final SegmentedKeyValueStorage storage) {
    // NOOP
    // not need to reset anything in full mode
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> storageToPairStream(
      final SegmentedKeyValueStorage storage,
      final Hash accountHash,
      final Bytes startKeyHash,
      final Function<Bytes, Bytes> valueMapper) {
    return Stream.empty();
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> storageToPairStream(
      final SegmentedKeyValueStorage storage,
      final Hash accountHash,
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final Function<Bytes, Bytes> valueMapper) {
    return Stream.empty();
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> accountsToPairStream(
      final SegmentedKeyValueStorage storage, final Bytes startKeyHash, final Bytes32 endKeyHash) {
    return Stream.empty();
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> accountsToPairStream(
      final SegmentedKeyValueStorage storage, final Bytes startKeyHash) {
    return Stream.empty();
  }
}
