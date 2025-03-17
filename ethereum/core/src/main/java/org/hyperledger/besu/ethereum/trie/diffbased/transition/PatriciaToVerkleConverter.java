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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Converts accounts and storage from a Patricia Merkle Trie (Bonsai) WorldState to a Verkle-based
 * WorldState.
 */
public class PatriciaToVerkleConverter {

  private static final Bytes NEXT_ACCOUNT_KEY =
      Bytes.wrap("NEXT_ACCOUNT_KEY".getBytes(StandardCharsets.UTF_8));
  private static final Bytes NEXT_STORAGE_KEY =
      Bytes.wrap("NEXT_STORAGE_KEY".getBytes(StandardCharsets.UTF_8));
  private static final Map<Bytes, Bytes> PRE_IMAGES = new HashMap<>();

  /**
   * Adds a (hash -> preimage) entry to the static map.
   *
   * @param hash the hash of the key
   * @param key the original preimage key
   */
  public static void addPreImage(final Bytes hash, final Bytes key) {
    PRE_IMAGES.put(hash, key);
  }

  /**
   * Converts accounts and their storage from a BonsaiWorldState (Patricia / Merkle) to a
   * VerkleWorldState. If the limit is reached, it returns a MigrationProgress holding the next
   * account and storage key.
   *
   * @param bonsaiWorldState the source Patricia (Merkle) state
   * @param verkleWorldState the target Verkle state
   * @param maxToConvert the maximum number of entries to convert
   */
  public static void convert(
      final BonsaiWorldState bonsaiWorldState,
      final VerkleWorldState verkleWorldState,
      final int maxToConvert) {
    VerkleWorldStateUpdateAccumulator verkleUpdateAccumulator = verkleWorldState.getAccumulator();
    MigrationProgress migrationProgress = loadMigrationProgress(verkleUpdateAccumulator);
    AtomicInteger convertedEntriesCount = new AtomicInteger(0);

    // Iterate over accounts in Bonsai (Merkle) and convert them
    bonsaiWorldState
        .getWorldStateStorage()
        .streamFlatAccounts(
            migrationProgress.getNextAccountAndReset(),
            account -> {
              final Hash accountHash = Hash.wrap(account.getFirst());
              final Address address = Address.wrap(PRE_IMAGES.get(accountHash));
              BonsaiAccount merkleAccount =
                  BonsaiAccount.fromRLP(bonsaiWorldState, address, account.getSecond(), false);

              // If the account has storage, migrate storage slots
              if (!merkleAccount.isStorageEmpty()
                  && !migrationProgress.isStorageAccountFullyMigrated()) {
                NavigableMap<Bytes32, Bytes> storages =
                    bonsaiWorldState
                        .getWorldStateStorage()
                        .streamFlatStorages(
                            accountHash,
                            migrationProgress.getNextStorageKeyAndReset(),
                            storage -> {
                              if (convertedEntriesCount.get() >= maxToConvert) {
                                migrationProgress.setNextStorageKey(storage.getFirst());
                                return false;
                              }
                              convertedEntriesCount.incrementAndGet();
                              return true;
                            });

                if (!migrationProgress.hasNextStorage()) {
                  migrationProgress.markStorageAccountFullyMigrated();
                }

                StorageConsumingMap<StorageSlotKey, DiffBasedValue<UInt256>> storageMap =
                    verkleUpdateAccumulator
                        .getStorageToUpdate()
                        .computeIfAbsent(
                            merkleAccount.getAddress(),
                            addr ->
                                new StorageConsumingMap<>(
                                    addr, new ConcurrentHashMap<>(), (a, v) -> {}));

                // Convert each storage entry in parallel
                storages.entrySet().parallelStream()
                    .forEach(
                        entry -> {
                          Hash slotHash = Hash.wrap(entry.getKey());
                          StorageSlotKey storageSlotKey =
                              new StorageSlotKey(
                                  slotHash,
                                  Optional.of(UInt256.fromBytes(PRE_IMAGES.get(slotHash))));
                          if (verkleWorldState
                              .getStorageValueByStorageSlotKey(
                                  merkleAccount.getAddress(), storageSlotKey)
                              .isEmpty()) {
                            storageMap.putIfAbsent(
                                storageSlotKey,
                                new DiffBasedValue<>(null, UInt256.fromBytes(entry.getValue())));
                          }
                        });
              }

              // Convert the account itself if still under the limit
              if (convertedEntriesCount.get() < maxToConvert) {
                migrationProgress.clearNextStorageKey();

                if (verkleWorldState.get(merkleAccount.getAddress()) == null) {
                  VerkleAccount verkleAccount =
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

                  verkleUpdateAccumulator
                      .getAccountsToUpdate()
                      .putIfAbsent(
                          verkleAccount.getAddress(), new DiffBasedValue<>(null, verkleAccount));
                  if (verkleAccount.hasCode()) {
                    verkleUpdateAccumulator
                        .getCodeToUpdate()
                        .putIfAbsent(
                            verkleAccount.getAddress(),
                            new DiffBasedValue<>(null, verkleAccount.getCode()));
                    convertedEntriesCount.addAndGet(
                        TrieKeyUtils.chunkifyCode(verkleAccount.getCode()).size());
                  }
                }
                convertedEntriesCount.incrementAndGet();
                return true;
              }

              // If we've reached the limit, record this as the next account
              migrationProgress.setNextAccount(accountHash);
              return false;
            });

    if (!migrationProgress.hasNextAccount()) {
      migrationProgress.markAccountsFullyMigrated();
    }

    // Persist the migration process back to the accumulator
    writeMigrationProgress(migrationProgress, verkleUpdateAccumulator);
  }

  /**
   * Loads the previously saved migration progress from the accumulator.
   *
   * @param accumulator the accumulator to read from.
   * @return a {@link MigrationProgress} instance holding the migration process state.
   */
  private static MigrationProgress loadMigrationProgress(
      final VerkleWorldStateUpdateAccumulator accumulator) {
    Bytes nextAccount = accumulator.getExtraField(NEXT_ACCOUNT_KEY).getUpdated();
    Bytes nextStorageKey = accumulator.getExtraField(NEXT_STORAGE_KEY).getUpdated();
    return new MigrationProgress(
        Optional.ofNullable(nextAccount), Optional.ofNullable(nextStorageKey));
  }

  /**
   * Writes the current migration progress back to the accumulator.
   *
   * @param progress the migration progress to store.
   * @param accumulator the accumulator to update.
   */
  private static void writeMigrationProgress(
      final MigrationProgress progress, final VerkleWorldStateUpdateAccumulator accumulator) {
    accumulator.getExtraField(NEXT_ACCOUNT_KEY).setUpdated(progress.getNextAccountAndReset());
    accumulator.getExtraField(NEXT_STORAGE_KEY).setUpdated(progress.getNextStorageKeyAndReset());
  }

  /** Holds the migration progress, tracking the next account and storage key. */
  private static final class MigrationProgress {

    private Optional<Bytes> nextAccount;
    private Optional<Bytes> nextStorageKey;

    /**
     * Constructor to initialize the migration progress.
     *
     * @param nextAccount The next account to process.
     * @param nextStorageKey The next storage key to process.
     */
    private MigrationProgress(
        final Optional<Bytes> nextAccount, final Optional<Bytes> nextStorageKey) {
      this.nextAccount = nextAccount;
      this.nextStorageKey = nextStorageKey;
    }

    /**
     * Retrieves the next account to process and resets it.
     *
     * @return The next account, or a zero value if none exists.
     */
    public Bytes getNextAccountAndReset() {
      return nextAccount
          .map(
              k -> {
                clearNextAccount();
                return k;
              })
          .orElse(Bytes32.ZERO);
    }

    /**
     * Retrieves the next storage key and resets it.
     *
     * @return The next storage key, or a zero value if none exists.
     */
    public Bytes getNextStorageKeyAndReset() {
      return nextStorageKey
          .map(
              k -> {
                clearNextStorageKey();
                return k;
              })
          .orElse(Bytes32.ZERO);
    }

    /**
     * Sets the next account to be processed.
     *
     * @param nextAccount The account to set.
     */
    public void setNextAccount(final Bytes nextAccount) {
      this.nextAccount = Optional.ofNullable(nextAccount);
    }

    /**
     * Checks if there is a next account to process.
     *
     * @return true if there is a next account, false otherwise.
     */
    public boolean hasNextAccount() {
      return this.nextAccount.isPresent();
    }

    /** Marks all the accounts as fully migrated by setting an empty value. */
    public void markAccountsFullyMigrated() {
      this.nextAccount = Optional.of(Bytes.EMPTY);
    }

    /**
     * Sets the next storage key to be processed.
     *
     * @param nextStorageKey The storage key to set.
     */
    public void setNextStorageKey(final Bytes nextStorageKey) {
      this.nextStorageKey = Optional.ofNullable(nextStorageKey);
    }

    /** Marks the storage account as fully migrated by setting an empty value. */
    public void markStorageAccountFullyMigrated() {
      this.nextStorageKey = Optional.of(Bytes.EMPTY);
    }

    /**
     * Checks if there is a next storage key to process.
     *
     * @return true if there is a next storage key, false otherwise.
     */
    public boolean hasNextStorage() {
      return this.nextStorageKey.isPresent();
    }

    /**
     * Checks if the storage account has been fully migrated.
     *
     * @return true if fully migrated, false otherwise.
     */
    public boolean isStorageAccountFullyMigrated() {
      return this.nextStorageKey.map(Bytes.EMPTY::equals).orElse(false);
    }

    /** Clears the next account field, indicating all accounts have been processed. */
    public void clearNextAccount() {
      this.nextAccount = Optional.empty();
    }

    /** Clears the next storage key field, indicating all storage keys have been processed. */
    public void clearNextStorageKey() {
      this.nextStorageKey = Optional.empty();
    }
  }
}
