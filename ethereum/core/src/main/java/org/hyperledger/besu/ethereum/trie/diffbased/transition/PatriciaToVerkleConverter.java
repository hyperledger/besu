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
 * Converts accounts and storage from a Patricia (Bonsai) WorldState to a Verkle-based WorldState.
 */
public class PatriciaToVerkleConverter {

  public static final Bytes LAST_PROCESSED_ACCOUNT_KEY =
      Bytes.wrap("LAST_PROCESSED_ACCOUNT_KEY".getBytes(StandardCharsets.UTF_8));

  public static final Bytes LAST_PROCESSED_STORAGE_KEY =
      Bytes.wrap("LAST_PROCESSED_STORAGE_KEY".getBytes(StandardCharsets.UTF_8));

  private static final Map<Bytes, Bytes> PRE_IMAGES = new HashMap<>();

  /**
   * Adds a (hash -> preimage) entry to the static map.
   *
   * @param hash the hash
   * @param key the preimage key
   */
  public static void addPreImage(final Bytes hash, final Bytes key) {
    PRE_IMAGES.put(hash, key);
  }

  /**
   * Converts accounts and their storage from a {@link BonsaiWorldState} (Patricia / Merkle) to a
   * {@link VerkleWorldState}. Returns a {@link LastProcessedState} holding the last processed
   * account and storage key (if the limit was reached).
   *
   * @param bonsaiWorldState the source Patricia (Merkle) state
   * @param verkleWorldState the target Verkle state
   * @param maxToConvert the maximum number of entries to convert
   */
  public static void convert(
      final BonsaiWorldState bonsaiWorldState,
      final VerkleWorldState verkleWorldState,
      final int maxToConvert) {

    final VerkleWorldStateUpdateAccumulator verkleUpdateAccumulator =
        verkleWorldState.getAccumulator();

    final LastProcessedState lastProcessedState = loadLastProcessedState(verkleUpdateAccumulator);

    final AtomicInteger convertedEntriesCount = new AtomicInteger(0);

    // Iterate over all accounts in the Bonsai (Merkle) and convert them
    bonsaiWorldState
        .getWorldStateStorage()
        .streamFlatAccounts(
            lastProcessedState.lastProcessedAccount(),
            account -> {
              final Hash accountHash = Hash.wrap(account.getFirst());
              // Build the BonsaiAccount from RLP
              final BonsaiAccount merkleAccount =
                  BonsaiAccount.fromRLP(
                      bonsaiWorldState,
                      Address.wrap(
                          PRE_IMAGES.get(accountHash)), // Replace with the actual address if needed
                      account.getSecond(),
                      false);
              // Convert storage slots if the account has any
              if (!merkleAccount.isStorageEmpty()) {
                final NavigableMap<Bytes32, Bytes> storages =
                    bonsaiWorldState
                        .getWorldStateStorage()
                        .streamFlatStorages(
                            accountHash,
                            lastProcessedState.lastProcessedStorageKey(),
                            storage -> {
                              // If under the limit, keep going
                              if (convertedEntriesCount.get() < maxToConvert) {
                                convertedEntriesCount.incrementAndGet();
                                return true;
                              }
                              // Otherwise, record next slot to migrate and stop
                              lastProcessedState.setLastProcessedStorageKey(storage.getFirst());
                              return false;
                            });

                // Convert each storage entry in parallel
                storages.entrySet().parallelStream()
                    .forEach(
                        entry -> {
                          final Hash slotHash = Hash.wrap(entry.getKey());
                          // Replace with actual slot key extraction logic if available
                          final UInt256 slotKey = UInt256.fromBytes(PRE_IMAGES.get(slotHash));

                          // If this slot doesn't exist in the Verkle trie, add it
                          if (verkleWorldState
                              .getPriorStorageValue(merkleAccount.getAddress(), slotKey)
                              .isEmpty()) {
                            final UInt256 slotValue = UInt256.fromBytes(entry.getValue());
                            verkleUpdateAccumulator
                                .getStorageToUpdate()
                                .computeIfAbsent(
                                    merkleAccount.getAddress(),
                                    address ->
                                        new StorageConsumingMap<>(
                                            address, new ConcurrentHashMap<>(), (a, v) -> {}))
                                .putIfAbsent(
                                    new StorageSlotKey(slotHash, Optional.of(slotKey)),
                                    new DiffBasedValue<>(null, slotValue));
                          }
                        });
              }

              // Convert the account itself if still under the limit
              if (convertedEntriesCount.get() < maxToConvert) {
                // Clear any previously recorded "next to migrate" data
                lastProcessedState.clearLastProcessedAccount();
                lastProcessedState.clearLastProcessedStorageKey();

                // If the account is not already in the Verkle trie, add it
                if (verkleWorldState.get(merkleAccount.getAddress()) == null) {
                  final VerkleAccount verkleAccount =
                      new VerkleAccount(
                          verkleUpdateAccumulator,
                          merkleAccount.getAddress(),
                          merkleAccount.getAddressHash(),
                          merkleAccount.getNonce(),
                          merkleAccount.getBalance(),
                          merkleAccount.getCodeSize().orElse(0L),
                          merkleAccount.getCodeHash(),
                          false);

                  verkleUpdateAccumulator
                      .getAccountsToUpdate()
                      .putIfAbsent(
                          verkleAccount.getAddress(), new DiffBasedValue<>(null, verkleAccount));
                }
                // Always increment the counter, even if the account is already in Verkle
                convertedEntriesCount.incrementAndGet();
                return true;
              }

              // If we've reached the limit, record this as the next account
              lastProcessedState.setLastProcessedAccount(accountHash);

              return false;
            });

    // Persist the updated last-processed state back to the accumulator
    writeLastProcessedState(lastProcessedState, verkleUpdateAccumulator);
  }

  /**
   * Loads the previously saved "last processed account/storage key" from the accumulator's extra
   * fields (if any).
   *
   * @param verkleWorldStateUpdateAccumulator the accumulator to read from
   * @return a {@link LastProcessedState} instance
   */
  private static LastProcessedState loadLastProcessedState(
      final VerkleWorldStateUpdateAccumulator verkleWorldStateUpdateAccumulator) {
    final Bytes lastProcessedAccount =
        verkleWorldStateUpdateAccumulator.getExtraFields().get(LAST_PROCESSED_ACCOUNT_KEY);
    final Bytes lastProcessedStorageKey =
        verkleWorldStateUpdateAccumulator.getExtraFields().get(LAST_PROCESSED_STORAGE_KEY);
    return new LastProcessedState(
        Optional.ofNullable(lastProcessedAccount), Optional.ofNullable(lastProcessedStorageKey));
  }

  /**
   * Writes the given {@link LastProcessedState} back to the accumulator's extra fields.
   *
   * @param lastProcessedState the last-processed info to store
   * @param verkleWorldStateUpdateAccumulator the accumulator to update
   */
  private static void writeLastProcessedState(
      final LastProcessedState lastProcessedState,
      final VerkleWorldStateUpdateAccumulator verkleWorldStateUpdateAccumulator) {
    verkleWorldStateUpdateAccumulator.addExtraField(
        LAST_PROCESSED_ACCOUNT_KEY, lastProcessedState.lastProcessedAccount());
    verkleWorldStateUpdateAccumulator.addExtraField(
        LAST_PROCESSED_STORAGE_KEY, lastProcessedState.lastProcessedStorageKey());
  }

  /** Holds the account/storage info that was last processed or next to be migrated. */
  private static final class LastProcessedState {

    private Optional<Bytes> lastProcessedAccount;
    private Optional<Bytes> lastProcessedStorageKey;

    private LastProcessedState(
        final Optional<Bytes> lastProcessedAccount, final Optional<Bytes> lastProcessedStorageKey) {
      this.lastProcessedAccount = lastProcessedAccount;
      this.lastProcessedStorageKey = lastProcessedStorageKey;
    }

    public Bytes lastProcessedAccount() {
      return lastProcessedAccount.orElse(Bytes32.ZERO);
    }

    public Bytes lastProcessedStorageKey() {
      return lastProcessedStorageKey.orElse(Bytes32.ZERO);
    }

    public void setLastProcessedAccount(final Bytes lastProcessedAccount) {
      this.lastProcessedAccount = Optional.ofNullable(lastProcessedAccount);
    }

    public void setLastProcessedStorageKey(final Bytes lastProcessedStorageKey) {
      this.lastProcessedStorageKey = Optional.ofNullable(lastProcessedStorageKey);
    }

    public void clearLastProcessedAccount() {
      this.lastProcessedAccount = Optional.empty();
    }

    public void clearLastProcessedStorageKey() {
      this.lastProcessedStorageKey = Optional.empty();
    }
  }
}
