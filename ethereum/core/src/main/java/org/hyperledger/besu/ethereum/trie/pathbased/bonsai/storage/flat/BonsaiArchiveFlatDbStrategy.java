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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE_ARCHIVE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_ARCHIVE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_BLOCK_NUMBER_KEY;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.common.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.pathbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BonsaiArchiveFlatDbStrategy extends BonsaiFullFlatDbStrategy {
  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiveFlatDbStrategy.class);

  protected final Counter getAccountFromArchiveCounter;
  protected final Counter getStorageFromArchiveCounter;

  public BonsaiArchiveFlatDbStrategy(
      final MetricsSystem metricsSystem, final CodeStorageStrategy codeStorageStrategy) {
    super(metricsSystem, codeStorageStrategy);

    getAccountFromArchiveCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_from_archive_counter",
            "Total number of calls to get account that were from archived state");

    getStorageFromArchiveCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storage_from_archive_counter",
            "Total number of calls to get storage that were from archived state");
  }

  static final byte[] MAX_BLOCK_SUFFIX = Bytes.ofUnsignedLong(Long.MAX_VALUE).toArrayUnsafe();
  static final byte[] MIN_BLOCK_SUFFIX = Bytes.ofUnsignedLong(0L).toArrayUnsafe();
  public static final byte[] DELETED_ACCOUNT_VALUE = new byte[0];
  public static final byte[] DELETED_STORAGE_VALUE = new byte[0];

  private Optional<BonsaiContext> getStateArchiveContextForWrite(
      final SegmentedKeyValueStorage storage) {
    // For Bonsai archive get the flat DB context to use for writing archive entries. We add one
    // because we're working with the latest world state so putting new flat DB keys requires us to
    // +1 to it
    Optional<byte[]> archiveContext = storage.get(TRIE_BRANCH_STORAGE, WORLD_BLOCK_NUMBER_KEY);
    if (archiveContext.isPresent()) {
      try {
        return Optional.of(
            // The context for flat-DB PUTs is the block number recorded in the specified world
            // state, + 1
            new BonsaiContext(Bytes.wrap(archiveContext.get()).toLong() + 1));
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "World state archive context invalid format: "
                + new String(archiveContext.get(), StandardCharsets.UTF_8));
      }
    } else {
      // Archive flat-db entries cannot be PUT if we don't have block context
      throw new IllegalStateException("World state missing archive context");
    }
  }

  private Optional<BonsaiContext> getStateArchiveContextForRead(
      final SegmentedKeyValueStorage storage) {
    // For Bonsai archive get the flat DB context to use for reading archive entries
    Optional<byte[]> archiveContext = storage.get(TRIE_BRANCH_STORAGE, WORLD_BLOCK_NUMBER_KEY);
    if (archiveContext.isPresent()) {
      try {
        return Optional.of(
            // The context for flat-DB PUTs is the block number recorded in the specified world
            // state
            new BonsaiContext(Bytes.wrap(archiveContext.get()).toLong()));
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "World state archive context invalid format: "
                + new String(archiveContext.get(), StandardCharsets.UTF_8));
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getFlatAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final SegmentedKeyValueStorage storage) {

    getAccountCounter.inc();
    Optional<SegmentedKeyValueStorage.NearestKeyValue> accountFound;

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest =
        calculateArchiveKeyWithMaxSuffix(
            getStateArchiveContextForRead(storage), accountHash.toArrayUnsafe());

    // Find the nearest account state for this address and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestAccount =
        storage
            .getNearestBefore(ACCOUNT_INFO_STATE, keyNearest)
            .filter(found -> accountHash.commonPrefixLength(found.key()) >= accountHash.size());

    // If there isn't a match look in the archive DB segment
    if (nearestAccount.isEmpty()) {
      accountFound =
          storage
              .getNearestBefore(ACCOUNT_INFO_STATE_ARCHIVE, keyNearest)
              .filter(found -> accountHash.commonPrefixLength(found.key()) >= accountHash.size());

      if (accountFound.isPresent()) {
        getAccountFromArchiveCounter.inc();
      } else {
        getAccountNotFoundInFlatDatabaseCounter.inc();
      }
    } else {

      accountFound = nearestAccount;
      getAccountFoundInFlatDatabaseCounter.inc();
    }

    if (accountFound.isPresent()) {
      // The entry exists (so metrics are still incremented) but we don't return deleted values
      return accountFound
          .filter(
              found ->
                  !Arrays.areEqual(
                      DELETED_ACCOUNT_VALUE, found.value().orElse(DELETED_ACCOUNT_VALUE)))
          // return empty when we find a "deleted value key"
          .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);
    }

    return Optional.empty();
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> accountsToPairStream(
      final SegmentedKeyValueStorage storage, final Bytes startKeyHash, final Bytes32 endKeyHash) {
    final Stream<Pair<Bytes32, Bytes>> stream =
        storage
            .streamFromKey(
                ACCOUNT_INFO_STATE,
                calculateArchiveKeyNoContextMinSuffix(startKeyHash.toArrayUnsafe()),
                calculateArchiveKeyNoContextMaxSuffix(endKeyHash.toArrayUnsafe()))
            .map(e -> Bytes.of(calculateArchiveKeyNoContextMaxSuffix(trimSuffix(e.getKey()))))
            .distinct()
            .map(
                e ->
                    new Pair<>(
                        Bytes32.wrap(trimSuffix(e.toArrayUnsafe())),
                        Bytes.of(
                            storage.getNearestBefore(ACCOUNT_INFO_STATE, e).get().value().get())));
    return stream;
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> accountsToPairStream(
      final SegmentedKeyValueStorage storage, final Bytes startKeyHash) {
    final Stream<Pair<Bytes32, Bytes>> stream =
        storage
            .streamFromKey(
                ACCOUNT_INFO_STATE,
                calculateArchiveKeyNoContextMinSuffix(startKeyHash.toArrayUnsafe()))
            .map(e -> Bytes.of(calculateArchiveKeyNoContextMaxSuffix(trimSuffix(e.getKey()))))
            .distinct()
            .map(
                e ->
                    new Pair<Bytes32, Bytes>(
                        Bytes32.wrap(trimSuffix(e.toArrayUnsafe())),
                        Bytes.of(
                            storage.getNearestBefore(ACCOUNT_INFO_STATE, e).get().value().get())));
    return stream;
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> storageToPairStream(
      final SegmentedKeyValueStorage storage,
      final Hash accountHash,
      final Bytes startKeyHash,
      final Function<Bytes, Bytes> valueMapper) {
    return storage
        .streamFromKey(
            ACCOUNT_STORAGE_STORAGE,
            calculateArchiveKeyNoContextMinSuffix(
                calculateNaturalSlotKey(accountHash, Hash.wrap(Bytes32.wrap(startKeyHash)))))
        .map(e -> Bytes.of(calculateArchiveKeyNoContextMaxSuffix(trimSuffix(e.getKey()))))
        .takeWhile(pair -> pair.slice(0, Hash.SIZE).equals(accountHash))
        .distinct()
        .map(
            key ->
                new Pair<>(
                    Bytes32.wrap(trimSuffix(key.slice(Hash.SIZE).toArrayUnsafe())),
                    valueMapper.apply(
                        Bytes.of(
                                storage
                                    .getNearestBefore(ACCOUNT_STORAGE_STORAGE, key)
                                    .get()
                                    .value()
                                    .get())
                            .trimLeadingZeros())));
  }

  @Override
  protected Stream<Pair<Bytes32, Bytes>> storageToPairStream(
      final SegmentedKeyValueStorage storage,
      final Hash accountHash,
      final Bytes startKeyHash,
      final Bytes32 endKeyHash,
      final Function<Bytes, Bytes> valueMapper) {
    return storage
        .streamFromKey(
            ACCOUNT_STORAGE_STORAGE,
            calculateArchiveKeyNoContextMinSuffix(
                calculateNaturalSlotKey(accountHash, Hash.wrap(Bytes32.wrap(startKeyHash)))),
            calculateArchiveKeyNoContextMaxSuffix(
                calculateNaturalSlotKey(accountHash, Hash.wrap(endKeyHash))))
        .map(e -> Bytes.of(calculateArchiveKeyNoContextMaxSuffix(trimSuffix(e.getKey()))))
        .takeWhile(pair -> pair.slice(0, Hash.SIZE).equals(accountHash))
        .distinct()
        .map(
            key ->
                new Pair<>(
                    Bytes32.wrap(trimSuffix(key.slice(Hash.SIZE).toArrayUnsafe())),
                    valueMapper.apply(
                        Bytes.of(
                                storage
                                    .getNearestBefore(ACCOUNT_STORAGE_STORAGE, key)
                                    .get()
                                    .value()
                                    .get())
                            .trimLeadingZeros())));
  }

  /*
   * Puts the account data for the given account hash and block context.
   */
  @Override
  public void putFlatAccount(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes accountValue) {

    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed =
        calculateArchiveKeyWithMinSuffix(
            getStateArchiveContextForWrite(storage).get(), accountHash.toArrayUnsafe());

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, accountValue.toArrayUnsafe());
  }

  @Override
  public void removeFlatAccount(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash) {

    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed =
        calculateArchiveKeyWithMinSuffix(
            getStateArchiveContextForWrite(storage).get(), accountHash.toArrayUnsafe());

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, DELETED_ACCOUNT_VALUE);
  }

  private byte[] trimSuffix(final byte[] suffixedAddress) {
    return Arrays.copyOfRange(suffixedAddress, 0, suffixedAddress.length - 8);
  }

  /*
   * Retrieves the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  @Override
  public Optional<Bytes> getFlatStorageValueByStorageSlotKey(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final Supplier<Optional<Hash>> storageRootSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey,
      final SegmentedKeyValueStorage storage) {

    Optional<SegmentedKeyValueStorage.NearestKeyValue> storageFound;
    getStorageValueCounter.inc();

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, storageSlotKey.getSlotHash());
    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest =
        calculateArchiveKeyWithMaxSuffix(getStateArchiveContextForRead(storage), naturalKey);

    // Find the nearest storage for this address, slot key hash, and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestStorage =
        storage
            .getNearestBefore(ACCOUNT_STORAGE_STORAGE, keyNearest)
            .filter(
                found -> Bytes.of(naturalKey).commonPrefixLength(found.key()) >= naturalKey.length);

    // If there isn't a match look in the archive DB segment
    if (nearestStorage.isEmpty()) {
      // Check the archived storage as old state is moved out of the primary DB segment
      storageFound =
          storage
              .getNearestBefore(ACCOUNT_STORAGE_ARCHIVE, keyNearest)
              // don't return accounts that do not have a matching account hash
              .filter(
                  found ->
                      Bytes.of(naturalKey).commonPrefixLength(found.key()) >= naturalKey.length);

      if (storageFound.isPresent()) {
        getStorageFromArchiveCounter.inc();
      } else {
        getStorageValueNotFoundInFlatDatabaseCounter.inc();
      }
    } else {
      storageFound = nearestStorage;
      getStorageValueFlatDatabaseCounter.inc();
    }

    // The entry exists (so metrics are still incremented) but we don't return deleted values
    if (storageFound.isPresent()) {
      return storageFound
          // return empty when we find a "deleted value key"
          .filter(
              found ->
                  !Arrays.areEqual(
                      DELETED_STORAGE_VALUE, found.value().orElse(DELETED_STORAGE_VALUE)))
          // map NearestKey to Bytes-wrapped value
          .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);
    }

    return Optional.empty();
  }

  /*
   * Puts the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  @Override
  public void putFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash,
      final Bytes storageValue) {

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, slotHash);
    // keyNearest, use MIN_BLOCK_SUFFIX in the absence of a block context:
    byte[] keyNearest =
        calculateArchiveKeyWithMinSuffix(getStateArchiveContextForWrite(storage).get(), naturalKey);

    transaction.put(ACCOUNT_STORAGE_STORAGE, keyNearest, storageValue.toArrayUnsafe());
  }

  /*
   * Removes the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  @Override
  public void removeFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorage storage,
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash) {

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, slotHash);
    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed =
        calculateArchiveKeyWithMinSuffix(getStateArchiveContextForWrite(storage).get(), naturalKey);

    transaction.put(ACCOUNT_STORAGE_STORAGE, keySuffixed, DELETED_STORAGE_VALUE);
  }

  public static byte[] calculateNaturalSlotKey(final Hash accountHash, final Hash slotHash) {
    return Bytes.concatenate(accountHash, slotHash).toArrayUnsafe();
  }

  public static byte[] calculateArchiveKeyWithMinSuffix(
      final BonsaiContext context, final byte[] naturalKey) {
    return calculateArchiveKeyWithSuffix(Optional.of(context), naturalKey, MIN_BLOCK_SUFFIX);
  }

  public static byte[] calculateArchiveKeyNoContextMinSuffix(final byte[] naturalKey) {
    return Arrays.concatenate(naturalKey, MIN_BLOCK_SUFFIX);
  }

  public static byte[] calculateArchiveKeyNoContextMaxSuffix(final byte[] naturalKey) {
    return Arrays.concatenate(naturalKey, MAX_BLOCK_SUFFIX);
  }

  public static Bytes calculateArchiveKeyWithMaxSuffix(
      final Optional<BonsaiContext> context, final byte[] naturalKey) {
    return Bytes.of(calculateArchiveKeyWithSuffix(context, naturalKey, MAX_BLOCK_SUFFIX));
  }

  // TODO JF: move this out of this class so can be used with ArchiveCodeStorageStrategy without
  // being static
  public static byte[] calculateArchiveKeyWithSuffix(
      final Optional<BonsaiContext> context, final byte[] naturalKey, final byte[] orElseSuffix) {
    // TODO: this can be optimized, just for PoC now
    return Arrays.concatenate(
        naturalKey,
        context
            .flatMap(BonsaiContext::getBlockNumber)
            .map(Bytes::ofUnsignedLong)
            .map(Bytes::toArrayUnsafe)
            .orElseGet(
                () -> {
                  // TODO: remove or rate limit these warnings
                  LOG.atWarn().setMessage("Block context not present, using default suffix").log();
                  return orElseSuffix;
                }));
  }
}
