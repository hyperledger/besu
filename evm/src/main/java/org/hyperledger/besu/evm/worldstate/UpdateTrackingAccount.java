/*
 * Copyright ConsenSys AG.
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

package org.hyperledger.besu.evm.worldstate;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import javax.annotation.Nullable;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * A implementation of {@link MutableAccount} that tracks updates made to the account since the
 * creation of the updater this is linked to.
 *
 * <p>Note that in practice this only track the modified value of the nonce and balance, but doesn't
 * remind if those were modified or not (the reason being that any modification of an account imply
 * the underlying trie node will have to be updated, and so knowing if the nonce and balance where
 * updated or not doesn't matter, we just need their new value).
 */
public class UpdateTrackingAccount<A extends Account> implements MutableAccount, EvmAccount {
  private final Address address;
  private final Hash addressHash;

  @Nullable private A account; // null if this is a new account.

  private long nonce;
  private Wei balance;

  @Nullable private Bytes updatedCode; // Null if the underlying code has not been updated.
  @Nullable private Hash updatedCodeHash;

  // Only contains updated storage entries, but may contains entry with a value of 0 to signify
  // deletion.
  private final NavigableMap<UInt256, UInt256> updatedStorage;
  private boolean storageWasCleared = false;
  private boolean transactionBoundary = false;

  UpdateTrackingAccount(final Address address) {
    checkNotNull(address);
    this.address = address;
    this.addressHash = Hash.hash(this.address);
    this.account = null;

    this.nonce = 0;
    this.balance = Wei.ZERO;

    this.updatedCode = Bytes.EMPTY;
    this.updatedStorage = new TreeMap<>();
  }

  public UpdateTrackingAccount(final A account) {
    checkNotNull(account);

    this.address = account.getAddress();
    this.addressHash =
        (account instanceof UpdateTrackingAccount)
            ? ((UpdateTrackingAccount<?>) account).addressHash
            : Hash.hash(this.address);
    this.account = account;

    this.nonce = account.getNonce();
    this.balance = account.getBalance();

    this.updatedStorage = new TreeMap<>();
  }

  /**
   * The original account over which this tracks updates.
   *
   * @return The original account over which this tracks updates, or {@code null} if this is a newly
   *     created account.
   */
  public A getWrappedAccount() {
    return account;
  }

  public void setWrappedAccount(final A account) {
    if (this.account == null) {
      this.account = account;
      storageWasCleared = false;
    } else {
      throw new IllegalStateException("Already tracking a wrapped account");
    }
  }

  /**
   * Whether the code of the account was modified.
   *
   * @return {@code true} if the code was updated.
   */
  public boolean codeWasUpdated() {
    return updatedCode != null;
  }

  /**
   * A map of the storage entries that were modified.
   *
   * @return a map containing all entries that have been modified. This <b>may</b> contain entries
   *     with a value of 0 to signify deletion.
   */
  @Override
  public Map<UInt256, UInt256> getUpdatedStorage() {
    return updatedStorage;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public Hash getAddressHash() {
    return addressHash;
  }

  @Override
  public long getNonce() {
    return nonce;
  }

  @Override
  public void setNonce(final long value) {
    this.nonce = value;
  }

  @Override
  public Wei getBalance() {
    return balance;
  }

  @Override
  public void setBalance(final Wei value) {
    this.balance = value;
  }

  @Override
  public Bytes getCode() {
    // Note that we set code for new account, so it's only null if account isn't.
    return updatedCode == null ? account.getCode() : updatedCode;
  }

  @Override
  public Hash getCodeHash() {
    if (updatedCode == null) {
      // Note that we set code for new account, so it's only null if account isn't.
      return account.getCodeHash();
    } else {
      // Cache the hash of updated code to avoid DOS attacks which repeatedly request hash
      // of updated code and cause us to regenerate it.
      if (updatedCodeHash == null) {
        updatedCodeHash = Hash.hash(updatedCode);
      }
      return updatedCodeHash;
    }
  }

  @Override
  public boolean hasCode() {
    // Note that we set code for new account, so it's only null if account isn't.
    return updatedCode == null ? account.hasCode() : !updatedCode.isEmpty();
  }

  @Override
  public void setCode(final Bytes code) {
    this.updatedCode = code;
    this.updatedCodeHash = null;
  }

  void markTransactionBoundary() {
    this.transactionBoundary = true;
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    final UInt256 value = updatedStorage.get(key);
    if (value != null) {
      return value;
    }
    if (storageWasCleared) {
      return UInt256.ZERO;
    }

    // We haven't updated the key-value yet, so either it's a new account and it doesn't have the
    // key, or we should query the underlying storage for its existing value (which might be 0).
    return account == null ? UInt256.ZERO : account.getStorageValue(key);
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    if (transactionBoundary) {
      return getStorageValue(key);
    } else if (storageWasCleared || account == null) {
      return UInt256.ZERO;
    } else {
      return account.getOriginalStorageValue(key);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    final NavigableMap<Bytes32, AccountStorageEntry> entries;
    if (account != null) {
      entries = account.storageEntriesFrom(startKeyHash, limit);
    } else {
      entries = new TreeMap<>();
    }
    updatedStorage.entrySet().stream()
        .map(entry -> AccountStorageEntry.forKeyAndValue(entry.getKey(), entry.getValue()))
        .filter(entry -> entry.getKeyHash().compareTo(startKeyHash) >= 0)
        .forEach(entry -> entries.put(entry.getKeyHash(), entry));

    while (entries.size() > limit) {
      entries.remove(entries.lastKey());
    }
    return entries;
  }

  @Override
  public void setStorageValue(final UInt256 key, final UInt256 value) {
    updatedStorage.put(key, value);
  }

  @Override
  public void clearStorage() {
    storageWasCleared = true;
    updatedStorage.clear();
  }

  public boolean getStorageWasCleared() {
    return storageWasCleared;
  }

  public void setStorageWasCleared(final boolean storageWasCleared) {
    this.storageWasCleared = storageWasCleared;
  }

  @Override
  public String toString() {
    String storage = updatedStorage.isEmpty() ? "[not updated]" : updatedStorage.toString();
    if (updatedStorage.isEmpty() && storageWasCleared) {
      storage = "[cleared]";
    }
    return String.format(
        "%s -> {nonce: %s, balance:%s, code:%s, storage:%s }",
        address, nonce, balance, updatedCode == null ? "[not updated]" : updatedCode, storage);
  }

  @Override
  public MutableAccount getMutable() throws ModificationNotAllowedException {
    return this;
  }
}
