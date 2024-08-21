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
package org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.CachedPedersenHasher;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleFlatDbStrategy extends FlatDbStrategy {

  private static final int ACCOUNT_CACHE_SIZE = 250;
  private static final int STEM_PER_ACCOUNT_CACHE_SIZE = 100;

  protected final Counter getAccountNotFoundInFlatDatabaseCounter;

  protected final Counter getStorageValueNotFoundInFlatDatabaseCounter;

  private final Cache<Address, CachedPedersenHasher> perdersenHasherByAccount;
  private final Map<Address, Map<Bytes, List<Bytes>>> stemByAccount = new ConcurrentHashMap<>();

  public VerkleFlatDbStrategy(
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

    perdersenHasherByAccount = CacheBuilder.newBuilder().maximumSize(ACCOUNT_CACHE_SIZE).build();
  }

  public Optional<Bytes> getFlatAccount(
      final Address address, final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();
    final Optional<List<Bytes>> stemValues = getStemValues(address, UInt256.ZERO, storage);
    if (stemValues.isPresent()) {
      getStorageValueFlatDatabaseCounter.inc();
      return Optional.of(encodeAccountStemValues(stemValues.get()));
    }
    getAccountNotFoundInFlatDatabaseCounter.inc();
    return Optional.empty();
  }

  public Optional<Bytes> getFlatStorageValueByStorageSlotKey(
      final Address address,
      final StorageSlotKey storageSlotKey,
      final SegmentedKeyValueStorage storage) {
    getStorageValueCounter.inc();
    final UInt256 slotKey = storageSlotKey.getSlotKey().orElseThrow();
    final Optional<List<Bytes>> stemValues = getStemValues(address, slotKey, storage);
    if (stemValues.isPresent()) {
      getStorageValueFlatDatabaseCounter.inc();
      return Optional.of(stemValues.get().get(slotKey.toInt()));
    }
    getStorageValueNotFoundInFlatDatabaseCounter.inc();
    return Optional.empty();
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

  private Optional<List<Bytes>> getStemValues(
      final Address address, final Bytes32 index, final SegmentedKeyValueStorage storage) {
    Map<Bytes, List<Bytes>> stemMap = stemByAccount.get(address);
    if (stemMap != null) {
      final CachedPedersenHasher cachedPedersenHasher;
      try {
        cachedPedersenHasher =
            perdersenHasherByAccount.get(
                address,
                () ->
                    new CachedPedersenHasher(
                        STEM_PER_ACCOUNT_CACHE_SIZE, new ConcurrentHashMap<>()));
        final Bytes stem = cachedPedersenHasher.computeStem(address, index);
        final Map<Bytes, List<Bytes>> stems =
            stemByAccount.computeIfAbsent(address, __ -> new ConcurrentHashMap<>());
        return Optional.ofNullable(
            stems.computeIfAbsent(
                stem,
                s ->
                    storage
                        .get(TRIE_BRANCH_STORAGE, stem.toArrayUnsafe())
                        .map(bytes -> decodeStemNode(Bytes.of(bytes)))
                        .orElse(null)));
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
    return Optional.empty();
  }

  public void clearCache() {
    stemByAccount.clear();
  }

  // TODO use besu verkle trie code

  private List<Bytes> decodeStemNode(final Bytes encodedValues) {
    RLPInput input = new BytesValueRLPInput(encodedValues, false);
    input.enterList();

    input.skipNext(); // depth
    input.skipNext(); // commitment
    input.skipNext(); // leftCommitment
    input.skipNext(); // rightCommitment
    input.skipNext(); // leftScalar
    input.skipNext(); // rightScaler
    return input.readList(RLPInput::readBytes);
  }

  private Bytes encodeAccountStemValues(final List<Bytes> values) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeUInt256Scalar(UInt256.fromBytes(values.get(1))); // balance
    out.writeLongScalar(values.get(2).toLong()); // nonce
    out.writeBytes(values.get(3)); // codehash
    out.writeLongScalar(values.get(4).toLong()); // codesize
    out.endList();
    return out.encoded();
  }
}
