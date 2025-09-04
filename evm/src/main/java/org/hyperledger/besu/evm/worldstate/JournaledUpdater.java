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
package org.hyperledger.besu.evm.worldstate;

import org.hyperledger.besu.collections.undo.UndoMap;
import org.hyperledger.besu.collections.undo.UndoSet;
import org.hyperledger.besu.collections.undo.Undoable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The Journaled updater.
 *
 * @param <W> the WorldView type parameter
 */
public class JournaledUpdater<W extends WorldView, A extends Account> implements WorldUpdater {

  private final EvmConfiguration evmConfiguration;
  private final WorldUpdater parentWorld;
  private final AbstractWorldUpdater<W, A> rootWorld;
  private final UndoMap<Address, JournaledAccount> accounts;
  private final UndoSet<Address> deleted;
  private final HashSet<Address> touched;
  private final Map<Address, Long> touchMarks;
  private final long undoMark;

  /**
   * Instantiates a new Journaled updater.
   *
   * @param world the world
   * @param evmConfiguration the EVM Configuration parameters
   */
  @SuppressWarnings("unchecked")
  public JournaledUpdater(final WorldUpdater world, final EvmConfiguration evmConfiguration) {
    parentWorld = world;
    this.evmConfiguration = evmConfiguration;
    if (world instanceof JournaledUpdater<?, ?>) {
      JournaledUpdater<W, A> journaledUpdater = (JournaledUpdater<W, A>) world;
      accounts = journaledUpdater.accounts;
      deleted = journaledUpdater.deleted;
      touched = new HashSet<>();
      touchMarks = new HashMap<>();
      rootWorld = journaledUpdater.rootWorld;
    } else if (world instanceof AbstractWorldUpdater<?, ?>) {
      accounts = new UndoMap<>(new HashMap<>());
      deleted = UndoSet.of(new HashSet<>());
      touched = new HashSet<>();
      touchMarks = new HashMap<>();
      rootWorld = (AbstractWorldUpdater<W, A>) world;
    } else {
      throw new IllegalArgumentException(
          "WorldUpdater must be a JournaledWorldUpdater or an AbstractWorldUpdater");
    }
    undoMark = accounts.mark();
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return touched.stream()
        .filter(addr -> !deleted.contains(addr))
        .map(addr -> accounts.get(addr, touchMarks.get(addr)))
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> new ArrayList<>(touched.size())));
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return deleted.stream()
        .filter(addr -> !deleted.contains(addr, undoMark))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Remove all changes done by this layer. Rollback to the state prior to the updater's changes.
   */
  private void reset() {
    accounts.values().forEach(a -> a.undo(undoMark));
    accounts.undo(undoMark);
    deleted.undo(undoMark);
    touched.clear();
    touchMarks.clear();
  }

  @Override
  public void revert() {
    reset();
  }

  @Override
  public void commit() {
    if (parentWorld instanceof JournaledUpdater<?, ?> jw) {
      jw.touched.addAll(this.touched);
      touchMarks.forEach(jw.touchMarks::put);
      return;
    }

    for (final JournaledAccount a : accounts.values()) {
      final Address addr = a.getAddress();
      if (deleted.contains(addr)) {
        continue;
      }
      if (a.getWrappedAccount() == null) {
        final MutableAccount account = rootWorld.createAccount(addr, a.getNonce(), a.getBalance());
        if (a.codeWasUpdated()) {
          account.setCode(a.getCode());
        }
        if (a.getStorageWasCleared()) {
          account.clearStorage();
        }
        a.getUpdatedStorage().forEach(account::setStorageValue);
        a.setWrappedAccount(account);
      }
      a.commit();
    }
    deleted.forEach(parentWorld::deleteAccount);
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.of(parentWorld);
  }

  /** Mark transaction boundary. */
  @Override
  public void markTransactionBoundary() {
    accounts.values().forEach(JournaledAccount::markTransactionBoundary);
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    final JournaledAccount ja = new JournaledAccount(address);
    ja.setNonce(nonce);
    ja.setBalance(balance);
    accounts.put(address, ja);
    deleted.remove(address);
    markTouched(address);
    return new MarkingAccount(address, ja);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    if (deleted.contains(address)) {
      return null;
    }
    final JournaledAccount existing = accounts.get(address);
    if (existing != null) {
      touched.add(address);
      return new MarkingAccount(address, existing);
    }

    final MutableAccount origin = getForMutation(address);
    if (origin == null) {
      return null;
    } else {
      var newAccount = new JournaledAccount(origin);
      accounts.put(address, newAccount);
      touched.add(address);
      return new MarkingAccount(address, newAccount);
    }
  }

  @Override
  public MutableAccount getMutableFrozen(final Address address) {
    final Account account = getMutable(address);
    if (account == null) {
      return null;
    }
    final JournaledAccount frozen = (account instanceof JournaledAccount a) ? a : new JournaledAccount(account);
    frozen.becomeImmutable();
    return frozen;
  }

  protected MutableAccount getForMutation(final Address address) {
    final AbstractWorldUpdater<W, A> wrapped = this.rootWorld;
    final UpdateTrackingAccount<A> wrappedTracker = wrapped.updatedAccounts.get(address);
    if (wrappedTracker != null) {
      return wrappedTracker;
    }
    if (wrapped.deletedAccounts.contains(address)) {
      return null;
    }
    final A account = wrapped.getForMutation(address);
    return account == null ? null : wrapped.track(new UpdateTrackingAccount<>(account));
  }

  @Override
  public void deleteAccount(final Address address) {
    deleted.add(address);
    touched.remove(address);
    touchMarks.remove(address);
    // TODO: This is probably not necessary?
    var account = accounts.get(address);
    if (account != null) {
      account.setDeleted(true);
    }
  }

  private MutableAccount getMutable(final Address address) {
    if (deleted.contains(address, undoMark)) {
      return null;
    }

    // If touched in this layer, return a view at the last touch
    final Long lastTouch = touchMarks.get(address);
    if (lastTouch != null) {
      final JournaledAccount current = accounts.get(address);
      return current != null ? current.snapshot(lastTouch) : null;
    }

    // If existed at the start of this updater, return view at undoMark
    final JournaledAccount atStart = accounts.get(address, undoMark);
    if (atStart != null) {
      return atStart.snapshot(undoMark);
    }

    // Else fallback to root updater
    return rootWorld.getAccount(address);
  }

  @Override
  public Account get(final Address address) {
    if (deleted.contains(address, undoMark)) {
      return null;
    }

    // If touched in this layer, return a view at the last touch
    final Long lastTouch = touchMarks.get(address);
    if (lastTouch != null) {
      final JournaledAccount current = accounts.get(address);
      return current != null ? current.snapshot(lastTouch) : null;
    }

    // If existed at the start of this updater, return view at undoMark
    final JournaledAccount atStart = accounts.get(address, undoMark);
    if (atStart != null) {
      return atStart.snapshot(undoMark);
    }

    // Else fallback to root updater
    return rootWorld.get(address);
  }

  @Override
  public WorldUpdater updater() {
    return new JournaledUpdater<>(this, evmConfiguration);
  }

  private void markTouched(final Address address) {
    touched.add(address);
    touchMarks.put(address, Undoable.markState.get());
  }

  private class MarkingAccount implements MutableAccount {
    private final Address address;
    private final JournaledAccount delegate;

    MarkingAccount(final Address address, final JournaledAccount delegate) {
      this.address = address;
      this.delegate = delegate;
    }

    private void mark() {
      markTouched(address);
    }

    @Override
    public void setNonce(final long value) {
      delegate.setNonce(value);
      mark();
    }

    @Override
    public void setBalance(final Wei value) {
      delegate.setBalance(value);
      mark();
    }

    @Override
    public void setCode(final Bytes code) {
      delegate.setCode(code);
      mark();
    }

    @Override
    public void setStorageValue(final UInt256 key, final UInt256 value) {
      delegate.setStorageValue(key, value);
      mark();
    }

    @Override
    public void clearStorage() {
      delegate.clearStorage();
      mark();
    }

    @Override
    public Map<UInt256, UInt256> getUpdatedStorage() {
      return delegate.getUpdatedStorage();
    }

    @Override
    public void becomeImmutable() {
      delegate.becomeImmutable();
    }

    @Override
    public Address getAddress() {
      return delegate.getAddress();
    }

    @Override
    public Hash getAddressHash() {
      return delegate.getAddressHash();
    }

    @Override
    public long getNonce() {
      return delegate.getNonce();
    }

    @Override
    public Wei getBalance() {
      return delegate.getBalance();
    }

    @Override
    public Bytes getCode() {
      return delegate.getCode();
    }

    @Override
    public Hash getCodeHash() {
      return delegate.getCodeHash();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 key) {
      return delegate.getStorageValue(key);
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 key) {
      return delegate.getOriginalStorageValue(key);
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
        final Bytes32 startKeyHash, final int limit) {
      return delegate.storageEntriesFrom(startKeyHash, limit);
    }

    @Override
    public boolean isStorageEmpty() {
      return delegate.isStorageEmpty();
    }
  }
}
