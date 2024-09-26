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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE_FREEZER;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_FREEZER;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.CodeStorageStrategy;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.flat.FlatDbStrategy;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveFlatDbStrategy extends FullFlatDbStrategy {
  private final BonsaiContext context;
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveFlatDbStrategy.class);

  public ArchiveFlatDbStrategy(
      final BonsaiContext context,
      final MetricsSystem metricsSystem,
      final CodeStorageStrategy codeStorageStrategy) {
    super(metricsSystem, codeStorageStrategy);
    this.context = context;
  }

  static final byte[] MAX_BLOCK_SUFFIX = Bytes.ofUnsignedLong(Long.MAX_VALUE).toArrayUnsafe();
  static final byte[] MIN_BLOCK_SUFFIX = Bytes.ofUnsignedLong(0L).toArrayUnsafe();
  public static final byte[] DELETED_ACCOUNT_VALUE = new byte[0];
  public static final byte[] DELETED_CODE_VALUE = new byte[0];
  public static final byte[] DELETED_STORAGE_VALUE = new byte[0];

  @Override
  public Optional<Bytes> getFlatAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final SegmentedKeyValueStorage storage) {

    getAccountCounter.inc();
    Optional<Bytes> accountFound;

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest = calculateArchiveKeyWithMaxSuffix(context, accountHash.toArrayUnsafe());

    // Find the nearest account state for this address and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestAccount =
        storage
            .getNearestBefore(ACCOUNT_INFO_STATE, keyNearest)
            .filter(found -> accountHash.commonPrefixLength(found.key()) >= accountHash.size());

    // If there isn't a match look in the freezer DB segment
    if (nearestAccount.isEmpty()) {
      accountFound =
          storage
              .getNearestBefore(ACCOUNT_INFO_STATE_FREEZER, keyNearest)
              .filter(found -> accountHash.commonPrefixLength(found.key()) >= accountHash.size())
              .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);
    } else {
      accountFound =
          nearestAccount
              .filter(
                  found ->
                      !Arrays.areEqual(
                          DELETED_ACCOUNT_VALUE, found.value().orElse(DELETED_ACCOUNT_VALUE)))
              // return empty when we find a "deleted value key"
              .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);
    }

    if (accountFound.isPresent()) {
      // TODO - different metric for frozen lookups?
      getAccountFoundInFlatDatabaseCounter.inc();
    } else {
      getAccountNotFoundInFlatDatabaseCounter.inc();
    }

    return accountFound;
  }

  /*
   * Puts the account data for the given account hash and block context.
   */
  @Override
  public void putFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes accountValue) {

    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(context, accountHash.toArrayUnsafe());

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, accountValue.toArrayUnsafe());
  }

  @Override
  public void removeFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {

    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(context, accountHash.toArrayUnsafe());

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, DELETED_ACCOUNT_VALUE);
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

    Optional<Bytes> storageFound;
    getStorageValueCounter.inc();

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, storageSlotKey.getSlotHash());
    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest = calculateArchiveKeyWithMaxSuffix(context, naturalKey);

    // Find the nearest storage for this address, slot key hash, and block context
    Optional<SegmentedKeyValueStorage.NearestKeyValue> nearestStorage =
        storage
            .getNearestBefore(ACCOUNT_STORAGE_STORAGE, keyNearest)
            .filter(
                found -> Bytes.of(naturalKey).commonPrefixLength(found.key()) >= naturalKey.length);

    // If there isn't a match look in the freezer DB segment
    if (nearestStorage.isEmpty()) {
      // Check the frozen storage as old state is moved out of the primary DB segment
      storageFound =
          storage
              .getNearestBefore(ACCOUNT_STORAGE_FREEZER, keyNearest)
              // don't return accounts that do not have a matching account hash
              .filter(
                  found ->
                      Bytes.of(naturalKey).commonPrefixLength(found.key()) >= naturalKey.length)
              .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);
    } else {
      storageFound =
          nearestStorage
              // return empty when we find a "deleted value key"
              .filter(
                  found ->
                      !Arrays.areEqual(
                          DELETED_STORAGE_VALUE, found.value().orElse(DELETED_STORAGE_VALUE)))
              // map NearestKey to Bytes-wrapped value
              .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

      if (storageFound.isPresent()) {
        // TODO - different metric for frozen lookups?
        getStorageValueFlatDatabaseCounter.inc();
      } else {
        getStorageValueNotFoundInFlatDatabaseCounter.inc();
      }
    }

    return storageFound;
  }

  /*
   * Puts the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  @Override
  public void putFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash,
      final Bytes storage) {

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, slotHash);
    // keyNearest, use MIN_BLOCK_SUFFIX in the absence of a block context:
    byte[] keyNearest = calculateArchiveKeyWithMinSuffix(context, naturalKey);

    transaction.put(ACCOUNT_STORAGE_STORAGE, keyNearest, storage.toArrayUnsafe());
  }

  /*
   * Removes the storage value for the given account hash and storage slot key, using the world state root hash supplier, storage root supplier, and node loader.
   */
  @Override
  public void removeFlatAccountStorageValueByStorageSlotHash(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash slotHash) {

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, slotHash);
    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(context, naturalKey);

    transaction.put(ACCOUNT_STORAGE_STORAGE, keySuffixed, DELETED_STORAGE_VALUE);
  }

  public static byte[] calculateNaturalSlotKey(final Hash accountHash, final Hash slotHash) {
    return Bytes.concatenate(accountHash, slotHash).toArrayUnsafe();
  }

  public static byte[] calculateArchiveKeyWithMinSuffix(
      final BonsaiContext context, final byte[] naturalKey) {
    return calculateArchiveKeyWithSuffix(context, naturalKey, MIN_BLOCK_SUFFIX);
  }

  public static Bytes calculateArchiveKeyWithMaxSuffix(
      final BonsaiContext context, final byte[] naturalKey) {
    return Bytes.of(calculateArchiveKeyWithSuffix(context, naturalKey, MAX_BLOCK_SUFFIX));
  }

  // TODO JF: move this out of this class so can be used with ArchiveCodeStorageStrategy without
  // being static
  public static byte[] calculateArchiveKeyWithSuffix(
      final BonsaiContext context, final byte[] naturalKey, final byte[] orElseSuffix) {
    // TODO: this can be optimized, just for PoC now
    return Arrays.concatenate(
        naturalKey,
        context
            .getBlockHeader()
            .map(BlockHeader::getNumber)
            .map(Bytes::ofUnsignedLong)
            .map(Bytes::toArrayUnsafe)
            .orElseGet(
                () -> {
                  // TODO: remove or rate limit these warnings
                  LOG.atWarn().setMessage("Block context not present, using default suffix").log();
                  return orElseSuffix;
                }));
  }

  @Override
  public void updateBlockContext(final BlockHeader blockHeader) {
    context.setBlockHeader(blockHeader);
  }

  @Override
  public FlatDbStrategy contextSafeClone() {
    return new ArchiveFlatDbStrategy(context.copy(), metricsSystem, codeStorageStrategy);
  }
}
