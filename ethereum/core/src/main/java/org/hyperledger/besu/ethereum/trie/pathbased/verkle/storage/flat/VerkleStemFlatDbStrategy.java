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

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.cache.preloader.StemPreloader;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.trie.verkle.util.SuffixTreeDecoder;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Strategy for managing Verkle accounts in the flat database using a stem-based structure.
 *
 * <p>Unlike the legacy approach of saving by account, slot, and code, this strategy saves by stem
 * and code. This allows direct access to the leaves of the Verkle trie for stems, reducing
 * duplication of data.
 */
public class VerkleStemFlatDbStrategy extends FlatDbStrategy {

  protected final Counter getAccountNotFoundInFlatDatabaseCounter;

  protected final Counter getStorageValueNotFoundInFlatDatabaseCounter;

  public VerkleStemFlatDbStrategy(
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
      final StemPreloader stemPreloader,
      final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();

    final Bytes preloadedStemId = stemPreloader.preloadAccountStem(address);
    final Optional<List<Optional<Bytes32>>> stem =
        getStem(preloadedStemId, storage).map(this::decodeStemNode);
    Optional<VerkleAccount> accountFound =
        stem.flatMap(
            values ->
                values
                    .getFirst()
                    .map(
                        basicDataLeaf ->
                            new VerkleAccount(
                                context,
                                address,
                                address.addressHash(),
                                SuffixTreeDecoder.decodeNonce(basicDataLeaf),
                                Wei.of(SuffixTreeDecoder.decodeBalance(basicDataLeaf)),
                                SuffixTreeDecoder.decodeCodeSize(basicDataLeaf),
                                Hash.wrap(values.get(1).orElse(Hash.EMPTY_TRIE_HASH)),
                                true)));
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
      final StemPreloader stemPreloader,
      final SegmentedKeyValueStorage storage) {
    getStorageValueCounter.inc();
    final Bytes preloadSlotStemId = stemPreloader.preloadSlotStems(address, storageSlotKey);
    final Optional<List<Optional<Bytes32>>> stem =
        getStem(preloadSlotStemId, storage).map(this::decodeStemNode);
    final Optional<Bytes> storageFound =
        stem.flatMap(
            values ->
                values.get(
                    TrieKeyUtils.getStorageKeySuffix(storageSlotKey.getSlotKey().orElseThrow())
                        .toInt()));
    if (storageFound.isPresent()) {
      getStorageValueFlatDatabaseCounter.inc();
    } else {
      getStorageValueNotFoundInFlatDatabaseCounter.inc();
    }
    return storageFound;
  }

  private Optional<Bytes> getStem(final Bytes stem, final SegmentedKeyValueStorage storage) {
    return storage.get(TRIE_BRANCH_STORAGE, stem.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public void putFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes accountValue) {
    // nothing to do with stem flat db
  }

  @Override
  public void removeFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {
    // nothing to do with stem flat db
  }

  @Override
  public void putFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash,
      final Bytes storage) {
    // nothing to do with stem flat db
  }

  @Override
  public void removeFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash) {
    // nothing to do with stem flat db
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

  private List<Optional<Bytes32>> decodeStemNode(final Bytes encodedValues) {
    RLPInput input = new BytesValueRLPInput(encodedValues, false);
    input.enterList();
    input.skipNext(); // depth
    input.skipNext(); // commitment
    input.skipNext(); // leftCommitment
    input.skipNext(); // rightCommitment
    input.skipNext(); // leftScalar
    input.skipNext(); // rightScalar
    return input.readList(
        rlpInput -> {
          Bytes bytes = rlpInput.readBytes();
          if (bytes.isEmpty()) {
            return Optional.empty();
          } else {
            return Optional.of(Bytes32.leftPad(bytes));
          }
        });
  }
}
