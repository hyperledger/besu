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
package org.hyperledger.besu.plugin.services.trielogs;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Manages the migration progress of accounts and storage slots from Bonsai (Patricia Merkle Trie)
 * to Verkle.
 *
 * <p>This class keeps track of the next account and storage key to process, allowing for an
 * incremental migration process with the ability to resume from the last processed point.
 */
public final class StateMigrationLog {

  private Optional<Bytes> nextAccount;
  private Optional<Bytes> nextStorageKey;
  private final long maxToConvert;

  /**
   * Initializes the migration log with specific progress tracking parameters.
   *
   * @param nextAccount The next account to process.
   * @param nextStorageKey The next storage key to process.
   * @param maxToConvert The maximum number of elements to migrate per batch.
   */
  public StateMigrationLog(
      final Optional<Bytes> nextAccount,
      final Optional<Bytes> nextStorageKey,
      final long maxToConvert) {
    this.nextAccount = nextAccount;
    this.nextStorageKey = nextStorageKey;
    this.maxToConvert = maxToConvert;
  }

  /**
   * Initializes a new migration log with an empty state.
   *
   * @param maxToConvert The maximum number of elements to migrate per batch.
   */
  public StateMigrationLog(final long maxToConvert) {
    this(Optional.empty(), Optional.empty(), maxToConvert);
  }

  /**
   * Retrieves the maximum number of elements that can be migrated per batch.
   *
   * @return The batch migration limit.
   */
  public long getMaxToConvert() {
    return maxToConvert;
  }

  /**
   * Retrieves the next account to process.
   *
   * @return The next account, or {@code Bytes32.ZERO} if none exists.
   */
  public Bytes getNextAccount() {
    return nextAccount.orElse(Bytes32.ZERO);
  }

  /**
   * Retrieves and clears the next account to process.
   *
   * @return The next account, or {@code Bytes32.ZERO} if none exists.
   */
  public Bytes getNextAccountAndReset() {
    return nextAccount
        .map(
            account -> {
              clearNextAccount();
              return account;
            })
        .orElse(Bytes32.ZERO);
  }

  /**
   * Retrieves the next storage key to process.
   *
   * @return The next storage key, or {@code Bytes32.ZERO} if none exists.
   */
  public Bytes getNextStorageKey() {
    return nextStorageKey.orElse(Bytes32.ZERO);
  }

  /**
   * Retrieves and clears the next storage key to process.
   *
   * @return The next storage key, or {@code Bytes32.ZERO} if none exists.
   */
  public Bytes getNextStorageKeyAndReset() {
    return nextStorageKey
        .map(
            storageKey -> {
              clearNextStorageKey();
              return storageKey;
            })
        .orElse(Bytes32.ZERO);
  }

  /**
   * Sets the next account to be migrated.
   *
   * @param nextAccount The account hash to set.
   */
  public void setNextAccount(final Bytes nextAccount) {
    this.nextAccount = Optional.ofNullable(nextAccount);
  }

  /**
   * Checks if there is a next account to process.
   *
   * @return {@code true} if there is a next account, otherwise {@code false}.
   */
  public boolean hasNextAccount() {
    return nextAccount.isPresent();
  }

  /** Marks all accounts as fully migrated. */
  public void markAccountsFullyMigrated() {
    this.nextAccount = Optional.of(Bytes.EMPTY);
  }

  /**
   * Checks if all accounts have been fully migrated.
   *
   * @return {@code true} if migration is complete, otherwise {@code false}.
   */
  public boolean isAccountsFullyMigrated() {
    return nextAccount.map(Bytes.EMPTY::equals).orElse(false);
  }

  /**
   * Sets the next storage key to be migrated.
   *
   * @param nextStorageKey The storage key to set.
   */
  public void setNextStorageKey(final Bytes nextStorageKey) {
    this.nextStorageKey = Optional.ofNullable(nextStorageKey);
  }

  /** Marks the storage migration as complete. */
  public void markStorageAccountFullyMigrated() {
    this.nextStorageKey = Optional.of(Bytes.EMPTY);
  }

  /**
   * Checks if there is a next storage key to process.
   *
   * @return {@code true} if there is a next storage key, otherwise {@code false}.
   */
  public boolean hasNextStorage() {
    return nextStorageKey.isPresent();
  }

  /**
   * Checks if all storage accounts have been fully migrated.
   *
   * @return {@code true} if migration is complete, otherwise {@code false}.
   */
  public boolean isStorageAccountFullyMigrated() {
    return nextStorageKey.map(Bytes.EMPTY::equals).orElse(false);
  }

  /** Clears the next account field, indicating all accounts have been processed. */
  public void clearNextAccount() {
    this.nextAccount = Optional.empty();
  }

  /** Clears the next storage key field, indicating all storage keys have been processed. */
  public void clearNextStorageKey() {
    this.nextStorageKey = Optional.empty();
  }

  /**
   * Checks if migration is still in progress.
   *
   * @return {@code true} if migration is ongoing, otherwise {@code false}.
   */
  public boolean isMigrationInProgress() {
    return !isAccountsFullyMigrated();
  }
}
