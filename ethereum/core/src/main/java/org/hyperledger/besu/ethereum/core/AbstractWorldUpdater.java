/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.core;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * An abstract implementation of a {@link WorldUpdater} that buffers update over the {@link
 * WorldView} provided in the constructor in memory.
 *
 * <p>Concrete implementation have to implement the {@link #commit()} method.
 */
public abstract class AbstractWorldUpdater<W extends WorldView, A extends Account>
    implements WorldUpdater {

  private final W world;

  private final Map<Address, UpdateTrackingAccount<A>> updatedAccounts = new HashMap<>();
  private final Set<Address> deletedAccounts = new HashSet<>();

  protected AbstractWorldUpdater(final W world) {
    this.world = world;
  }

  protected abstract A getForMutation(Address address);

  protected UpdateTrackingAccount<A> track(final UpdateTrackingAccount<A> account) {
    final Address address = account.getAddress();
    updatedAccounts.put(address, account);
    deletedAccounts.remove(address);
    return account;
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    final UpdateTrackingAccount<A> account = new UpdateTrackingAccount<>(address);
    account.setNonce(nonce);
    account.setBalance(balance);
    return track(account);
  }

  @Override
  public Account get(final Address address) {
    // We may have updated it already, so check that first.
    final MutableAccount existing = updatedAccounts.get(address);
    if (existing != null) {
      return existing;
    }
    if (deletedAccounts.contains(address)) {
      return null;
    }
    return world.get(address);
  }

  @Override
  public MutableAccount getMutable(final Address address) {
    // We may have updated it already, so check that first.
    final MutableAccount existing = updatedAccounts.get(address);
    if (existing != null) {
      return existing;
    }
    if (deletedAccounts.contains(address)) {
      return null;
    }

    // Otherwise, get it from our wrapped view and create a new update tracker.
    final A origin = getForMutation(address);
    if (origin == null) {
      return null;
    } else {
      return track(new UpdateTrackingAccount<>(origin));
    }
  }

  @Override
  public void deleteAccount(final Address address) {
    deletedAccounts.add(address);
    updatedAccounts.remove(address);
  }

  /**
   * Creates an updater that buffer updates on top of this updater.
   *
   * <p>
   *
   * @return a new updater on top of this updater. Updates made to the returned object will become
   *     visible on this updater when the returned updater is committed. Note however that updates
   *     to this updater <b>may or may not</b> be reflected to the created updater, so it is
   *     <b>strongly</b> advised to not update this updater until the returned one is discarded
   *     (either after having been committed, or because the updates it represent are meant to be
   *     discarded).
   */
  @Override
  public WorldUpdater updater() {
    return new StackedUpdater<>(this);
  }

  /**
   * The world view on top of which this buffer updates.
   *
   * @return The world view on top of which this buffer updates.
   */
  protected W wrappedWorldView() {
    return world;
  }

  /**
   * The accounts modified in this updater.
   *
   * @return The accounts modified in this updater.
   */
  protected Collection<UpdateTrackingAccount<A>> updatedAccounts() {
    return updatedAccounts.values();
  }

  /**
   * The accounts deleted as part of this updater.
   *
   * @return The accounts deleted as part of this updater.
   */
  protected Collection<Address> deletedAccounts() {
    return deletedAccounts;
  }

  /**
   * A implementation of {@link MutableAccount} that tracks updates made to the account since the
   * creation of the updater this is linked to.
   *
   * <p>Note that in practice this only track the modified value of the nonce and balance, but
   * doesn't remind if those were modified or not (the reason being that any modification of an
   * account imply the underlying trie node will have to be updated, and so knowing if the nonce and
   * balance where updated or not doesn't matter, we just need their new value).
   */
  public static class UpdateTrackingAccount<A extends Account> implements MutableAccount {
    private final Address address;

    @Nullable private final A account; // null if this is a new account.

    private long nonce;
    private Wei balance;
    private int version;

    @Nullable private BytesValue updatedCode; // Null if the underlying code has not been updated.
    @Nullable private Hash updatedCodeHash;

    // Only contains updated storage entries, but may contains entry with a value of 0 to signify
    // deletion.
    private final SortedMap<UInt256, UInt256> updatedStorage;
    private boolean storageWasCleared = false;

    UpdateTrackingAccount(final Address address) {
      checkNotNull(address);
      this.address = address;
      this.account = null;

      this.nonce = 0;
      this.balance = Wei.ZERO;
      this.version = Account.DEFAULT_VERSION;

      this.updatedCode = BytesValue.EMPTY;
      this.updatedStorage = new TreeMap<>();
    }

    UpdateTrackingAccount(final A account) {
      checkNotNull(account);

      this.address = account.getAddress();
      this.account = account;

      this.nonce = account.getNonce();
      this.balance = account.getBalance();
      this.version = account.getVersion();

      this.updatedStorage = new TreeMap<>();
    }

    /**
     * The original account over which this tracks updates.
     *
     * @return The original account over which this tracks updates, or {@code null} if this is a
     *     newly created account.
     */
    public A getWrappedAccount() {
      return account;
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
    public SortedMap<UInt256, UInt256> getUpdatedStorage() {
      return updatedStorage;
    }

    @Override
    public Address getAddress() {
      return address;
    }

    @Override
    public Hash getAddressHash() {
      return Hash.hash(getAddress());
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
    public BytesValue getCode() {
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
    public void setCode(final BytesValue code) {
      this.updatedCode = code;
    }

    @Override
    public void setVersion(final int version) {
      this.version = version;
    }

    @Override
    public int getVersion() {
      return version;
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
      return storageWasCleared || account == null
          ? UInt256.ZERO
          : account.getOriginalStorageValue(key);
    }

    @Override
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
  }

  static class StackedUpdater<W extends WorldView, A extends Account>
      extends AbstractWorldUpdater<AbstractWorldUpdater<W, A>, UpdateTrackingAccount<A>> {

    StackedUpdater(final AbstractWorldUpdater<W, A> world) {
      super(world);
    }

    @Override
    protected UpdateTrackingAccount<A> getForMutation(final Address address) {
      final AbstractWorldUpdater<W, A> wrapped = wrappedWorldView();
      final UpdateTrackingAccount<A> wrappedTracker = wrapped.updatedAccounts.get(address);
      if (wrappedTracker != null) {
        return wrappedTracker;
      }
      if (wrapped.deletedAccounts.contains(address)) {
        return null;
      }
      // The wrapped one isn't tracking that account. We're creating a tracking "for him" (but
      // don't add him yet to his tracking map) because we need it to satisfy the type system.
      // We will recognize this case in commit below and use that tracker "pay back" our
      // allocation, so this isn't lost.
      final A account = wrappedWorldView().getForMutation(address);
      return account == null ? null : new UpdateTrackingAccount<>(account);
    }

    @Override
    public Collection<Account> getTouchedAccounts() {
      return new ArrayList<>(updatedAccounts());
    }

    @Override
    public void revert() {
      deletedAccounts().clear();
      updatedAccounts().clear();
    }

    @Override
    public void commit() {
      final AbstractWorldUpdater<W, A> wrapped = wrappedWorldView();
      // Our own updates should apply on top of the updates we're stacked on top, so our deletions
      // may kill some of "their" updates, and our updates may review some of the account "they"
      // deleted.
      deletedAccounts().forEach(wrapped.updatedAccounts::remove);
      updatedAccounts().forEach(a -> wrapped.deletedAccounts.remove(a.getAddress()));

      // Then push our deletes and updates to the stacked ones.
      wrapped.deletedAccounts.addAll(deletedAccounts());

      for (final UpdateTrackingAccount<UpdateTrackingAccount<A>> update : updatedAccounts()) {
        UpdateTrackingAccount<A> existing = wrapped.updatedAccounts.get(update.getAddress());
        if (existing == null) {
          // If we don't track this account, it's either a new one or getForMutation above had
          // created a tracker to satisfy the type system above and we can reuse that now.
          existing = update.getWrappedAccount();
          if (existing == null) {
            // Brand new account, create our own version
            existing = new UpdateTrackingAccount<>(update.address);
          }
          wrapped.updatedAccounts.put(existing.address, existing);
        }
        existing.setNonce(update.getNonce());
        existing.setBalance(update.getBalance());
        if (update.codeWasUpdated()) {
          existing.setCode(update.getCode());
          existing.setVersion(update.getVersion());
        }
        if (update.getStorageWasCleared()) {
          existing.clearStorage();
        }
        update.getUpdatedStorage().forEach(existing::setStorageValue);
      }
    }
  }
}
