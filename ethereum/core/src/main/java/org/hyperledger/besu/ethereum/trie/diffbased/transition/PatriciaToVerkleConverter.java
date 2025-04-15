/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.StorageConsumingMap;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyUtils;
import org.hyperledger.besu.plugin.services.trielogs.StateMigrationLog;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Converts accounts and storage from a Patricia Merkle Trie (Bonsai) WorldState to a Verkle-based
 * WorldState.
 */
public class PatriciaToVerkleConverter {

  private static final Map<Bytes, Bytes> PRE_IMAGES = new HashMap<>();

  public static void addPreImage(final Bytes hash, final Bytes key) {
    PRE_IMAGES.put(hash, key);
  }

  /**
   * Converts accounts and storage from a Bonsai (Patricia Merkle Trie) world state to a
   * Verkle-based world state. The migration process respects a predefined limit.
   *
   * @param bonsaiWorldState The source Bonsai world state.
   * @param verkleWorldState The target Verkle world state.
   * @param migrationProgress Tracks migration progress to allow resumption.
   */
  public static void convert(
      final BonsaiWorldState bonsaiWorldState,
      final VerkleWorldState verkleWorldState,
      final StateMigrationLog migrationProgress) {

    final VerkleWorldStateUpdateAccumulator verkleUpdateAccumulator =
        verkleWorldState.getAccumulator();
    final AtomicInteger convertedEntriesCount = new AtomicInteger(0);

    bonsaiWorldState
        .getWorldStateStorage()
        .streamFlatAccounts(
            migrationProgress.getNextAccountAndReset(),
            account -> {
              final Hash accountHash = Hash.wrap(account.getFirst());
              final Address address = Address.wrap(PRE_IMAGES.get(accountHash));

              final BonsaiAccount merkleAccount =
                  BonsaiAccount.fromRLP(bonsaiWorldState, address, account.getSecond(), false);

              if (!merkleAccount.isStorageEmpty()
                  && !migrationProgress.isStorageAccountFullyMigrated()) {
                migrateStorage(
                    bonsaiWorldState,
                    verkleWorldState,
                    verkleUpdateAccumulator,
                    merkleAccount,
                    migrationProgress,
                    convertedEntriesCount,
                    accountHash);
              }

              return migrateAccount(
                  merkleAccount,
                  verkleWorldState,
                  verkleUpdateAccumulator,
                  migrationProgress,
                  convertedEntriesCount,
                  accountHash);
            });

    if (!migrationProgress.hasNextAccount()) {
      migrationProgress.markAccountsFullyMigrated();
    }
  }

  /**
   * Migrates the storage slots of an account from Bonsai to Verkle. This method ensures storage
   * migration does not exceed the predefined conversion limit.
   *
   * @param bonsaiWorldState The source Bonsai world state.
   * @param verkleWorldState The target Verkle world state.
   * @param verkleUpdateAccumulator Accumulator for Verkle state updates.
   * @param merkleAccount The Bonsai account being migrated.
   * @param migrationProgress Progress tracker for storage migration.
   * @param convertedEntriesCount Counter for converted storage entries.
   * @param accountHash The hash of the account being migrated.
   */
  private static void migrateStorage(
      final BonsaiWorldState bonsaiWorldState,
      final VerkleWorldState verkleWorldState,
      final VerkleWorldStateUpdateAccumulator verkleUpdateAccumulator,
      final BonsaiAccount merkleAccount,
      final StateMigrationLog migrationProgress,
      final AtomicInteger convertedEntriesCount,
      final Hash accountHash) {
    final NavigableMap<Bytes32, Bytes> storages =
        bonsaiWorldState
            .getWorldStateStorage()
            .streamFlatStorages(
                accountHash,
                migrationProgress.getNextStorageKeyAndReset(),
                storage -> {
                  if (convertedEntriesCount.get() >= migrationProgress.getMaxToConvert()) {
                    migrationProgress.setNextStorageKey(storage.getFirst());
                    return false;
                  }
                  convertedEntriesCount.incrementAndGet();
                  return true;
                });

    if (!migrationProgress.hasNextStorage()) {
      migrationProgress.markStorageAccountFullyMigrated();
    }

    final StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageMap =
        verkleUpdateAccumulator
            .getStorageToUpdate()
            .computeIfAbsent(
                merkleAccount.getAddress(),
                addr -> new StorageConsumingMap<>(addr, new ConcurrentHashMap<>(), (a, v) -> {}));

    storages.entrySet().parallelStream()
        .forEach(
            (entry) -> {
              final Hash slotHash = Hash.wrap(entry.getKey());
              final StorageSlotKey storageSlotKey =
                  new StorageSlotKey(
                      slotHash, Optional.of(UInt256.fromBytes(PRE_IMAGES.get(slotHash))));

              if (verkleWorldState
                  .getStorageValueByStorageSlotKey(merkleAccount.getAddress(), storageSlotKey)
                  .isEmpty()) {
                System.out.println(
                    "migrate storage "
                        + merkleAccount.getAddress()
                        + " "
                        + entry.getKey()
                        + " "
                        + entry.getValue());
                storageMap.putIfAbsent(
                    storageSlotKey,
                    new DiffBasedValue<>(
                        null, UInt256.fromBytes(RLP.decodeValue(entry.getValue()))));
              }
            });
  }

  /**
   * Migrates an individual account from Bonsai to Verkle. If the migration limit is reached, the
   * migration is paused and the account is marked for resumption.
   *
   * @param merkleAccount The Bonsai account being migrated.
   * @param verkleWorldState The target Verkle world state.
   * @param verkleUpdateAccumulator Accumulator for Verkle updates.
   * @param migrationProgress Migration progress tracker.
   * @param convertedEntriesCount Counter for converted accounts.
   * @param accountHash Hash of the account being processed.
   * @return true if migration can continue, false if the limit is reached.
   */
  private static boolean migrateAccount(
      final BonsaiAccount merkleAccount,
      final VerkleWorldState verkleWorldState,
      final VerkleWorldStateUpdateAccumulator verkleUpdateAccumulator,
      final StateMigrationLog migrationProgress,
      final AtomicInteger convertedEntriesCount,
      final Hash accountHash) {

    if (convertedEntriesCount.get() < migrationProgress.getMaxToConvert()) {
      migrationProgress.clearNextStorageKey();
      VerkleAccount verkleAccount =
          (VerkleAccount) verkleWorldState.get(merkleAccount.getAddress());
      if (verkleAccount == null) {
        verkleAccount =
            new VerkleAccount(
                verkleUpdateAccumulator,
                merkleAccount.getAddress(),
                merkleAccount.getAddressHash(),
                merkleAccount.getNonce(),
                merkleAccount.getBalance(),
                merkleAccount.getCodeSize().orElse(0L),
                merkleAccount.getCode(),
                merkleAccount.getCodeHash(),
                false);

        System.out.println("migrate " + merkleAccount.getAddress());
        verkleUpdateAccumulator
            .getAccountsToUpdate()
            .putIfAbsent(verkleAccount.getAddress(), new DiffBasedValue<>(null, verkleAccount));
      } else {
        verkleUpdateAccumulator
            .getAccountsToUpdate()
            .putIfAbsent(
                verkleAccount.getAddress(), new DiffBasedValue<>(verkleAccount, verkleAccount));
      }
      if (verkleAccount.hasCode()) {
        if (merkleAccount.getCodeHash().equals(verkleAccount.getCodeHash())) {
          verkleUpdateAccumulator
              .getCodeToUpdate()
              .putIfAbsent(
                  verkleAccount.getAddress(), new DiffBasedValue<>(null, verkleAccount.getCode()));
        }
        // Adjust conversion count based on the code chunkification process
        convertedEntriesCount.addAndGet(TrieKeyUtils.chunkifyCode(verkleAccount.getCode()).size());
      }

      convertedEntriesCount.incrementAndGet();
      return true;
    }

    migrationProgress.setNextAccount(accountHash);
    return false;
  }
}
