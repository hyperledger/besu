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
package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveFlatDbStrategy extends FullFlatDbStrategy {
  private final BonsaiContext context;
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveFlatDbStrategy.class);

  public ArchiveFlatDbStrategy(final BonsaiContext context, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.context = context;
  }

  static final byte[] MAX_BLOCK_SUFFIX = Bytes.ofUnsignedLong(Long.MAX_VALUE).toArrayUnsafe();
  static final byte[] MIN_BLOCK_SUFFIX = Bytes.ofUnsignedLong(0L).toArrayUnsafe();
  static final byte[] DELETED_ACCOUNT_VALUE = new byte[0];
  static final byte[] DELETED_CODE_VALUE = new byte[0];
  static final byte[] DELETED_STORAGE_VALUE = new byte[0];

  @Override
  public Optional<Bytes> getFlatAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest = calculateArchiveKeyWithMaxSuffix(accountHash.toArrayUnsafe());

    // use getNearest() with an account key that is suffixed by the block context
    final Optional<Bytes> accountFound =
        storage
            .getNearestTo(ACCOUNT_INFO_STATE, keyNearest)
            // return empty when we find a "deleted value key"
            .filter(
                found ->
                    !Arrays.areEqual(
                        DELETED_ACCOUNT_VALUE, found.value().orElse(DELETED_ACCOUNT_VALUE)))
            // don't return accounts that do not have a matching account hash
            .filter(found -> accountHash.commonPrefixLength(found.key()) >= accountHash.size())
            .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

    if (accountFound.isPresent()) {
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
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(accountHash.toArrayUnsafe());

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, accountValue.toArrayUnsafe());
  }

  @Override
  public void removeFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {

    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(accountHash.toArrayUnsafe());

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, DELETED_ACCOUNT_VALUE);
  }

  /*
   * Retrieves the code data for the given code hash and account hash and block context.
   */
  @Override
  public Optional<Bytes> getFlatCode(
      final Bytes32 codeHash, final Hash accountHash, final SegmentedKeyValueStorage storage) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {

      // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
      Bytes keyNearest = calculateArchiveKeyWithMaxSuffix(accountHash.toArrayUnsafe());

      // use getNearest() with an account key that is suffixed by the block context
      final Optional<Bytes> codeFound =
          storage
              .getNearestTo(CODE_STORAGE, keyNearest)
              // return empty when we find a "deleted value key"
              .filter(
                  found ->
                      !Arrays.areEqual(
                          DELETED_CODE_VALUE, found.value().orElse(DELETED_CODE_VALUE)))
              // map NearestKey to Bytes-wrapped value
              .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes)
              // check codeHash to sanity check the value and ensure we have the correct nearestKey:
              .filter(b -> Hash.hash(b).equals(codeHash));

      return codeFound;
    }
  }

  /*
   * Adds a "deleted key" code entry for the given account hash and block context.
   */
  @Override
  public void removeFlatCode(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {

    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(accountHash.toArrayUnsafe());

    transaction.put(CODE_STORAGE, keySuffixed, DELETED_CODE_VALUE);
  }

  /*
   * Puts the code data for the given code hash and account hash and block context.
   */
  @Override
  public void putFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes32 codeHash,
      final Bytes code) {

    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(accountHash.toArrayUnsafe());

    transaction.put(CODE_STORAGE, keySuffixed, code.toArrayUnsafe());
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
    getStorageValueCounter.inc();

    // get natural key from account hash and slot key
    byte[] naturalKey = calculateNaturalSlotKey(accountHash, storageSlotKey.getSlotHash());
    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest = calculateArchiveKeyWithMaxSuffix(naturalKey);

    // use getNearest() with a key that is suffixed by the block context
    final Optional<Bytes> storageFound =
        storage
            .getNearestTo(ACCOUNT_STORAGE_STORAGE, keyNearest)
            // return empty when we find a "deleted value key"
            .filter(
                found ->
                    !Arrays.areEqual(
                        DELETED_STORAGE_VALUE, found.value().orElse(DELETED_STORAGE_VALUE)))
            // don't return accounts that do not have a matching account hash and slotHash prefix
            .filter(
                found -> Bytes.of(naturalKey).commonPrefixLength(found.key()) >= naturalKey.length)
            // map NearestKey to Bytes-wrapped value
            .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

    if (storageFound.isPresent()) {
      getStorageValueFlatDatabaseCounter.inc();
    } else {
      getStorageValueNotFoundInFlatDatabaseCounter.inc();
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
    byte[] keyNearest = calculateArchiveKeyWithMinSuffix(naturalKey);

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
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(naturalKey);

    transaction.put(ACCOUNT_STORAGE_STORAGE, keySuffixed, DELETED_STORAGE_VALUE);
  }

  public byte[] calculateNaturalSlotKey(final Hash accountHash, final Hash slotHash) {
    return Bytes.concatenate(accountHash, slotHash).toArrayUnsafe();
  }

  public byte[] calculateArchiveKeyWithMinSuffix(final byte[] naturalKey) {
    return calculateArchiveKeyWithSuffix(naturalKey, MIN_BLOCK_SUFFIX);
  }

  public Bytes calculateArchiveKeyWithMaxSuffix(final byte[] naturalKey) {
    return Bytes.of(calculateArchiveKeyWithSuffix(naturalKey, MAX_BLOCK_SUFFIX));
  }

  public byte[] calculateArchiveKeyWithSuffix(final byte[] naturalKey, final byte[] orElseSuffix) {
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
    return new ArchiveFlatDbStrategy(context.copy(), metricsSystem);
  }
}
