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
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import org.hyperledger.besu.ethereum.vm.Code;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An abstract implementation of a {@link WorldUpdater} that buffers update over the {@link
 * WorldView} provided in the constructor in memory.
 *
 * <p>Concrete implementation have to implement the {@link #commit()} method.
 */
public abstract class AbstractWorldUpdater<W extends WorldView, A extends Account>
    implements WorldUpdater {

  private final W world;

  protected Map<Address, UpdateTrackingAccount<A>> updatedAccounts = new HashMap<>();
  protected Set<Address> deletedAccounts = new HashSet<>();
  protected final CodeCache codeCache;

  protected AbstractWorldUpdater(final W world, final CodeCache cache) {
    this.world = world;
    this.codeCache = cache;
  }

  protected AbstractWorldUpdater(final W world) {
    this.world = world;
    this.codeCache = new CodeCache();
  }

  protected abstract A getForMutation(Address address);

  protected UpdateTrackingAccount<A> track(final UpdateTrackingAccount<A> account) {
    final Address address = account.getAddress();
    updatedAccounts.put(address, account);
    deletedAccounts.remove(address);
    return account;
  }

  @Override
  public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
    final UpdateTrackingAccount<A> account = new UpdateTrackingAccount<>(address);
    account.setNonce(nonce);
    account.setBalance(balance);
    return new WrappedEvmAccount(track(account));
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
  public Optional<Code> getContract(final Account account) {
    return this.codeCache.getContract(account);
  }

  @Override
  public EvmAccount getAccount(final Address address) {
    // We may have updated it already, so check that first.
    final MutableAccount existing = updatedAccounts.get(address);
    if (existing != null) {
      return new WrappedEvmAccount(existing);
    }
    if (deletedAccounts.contains(address)) {
      return null;
    }

    // Otherwise, get it from our wrapped view and create a new update tracker.
    final A origin = getForMutation(address);
    if (origin == null) {
      return null;
    } else {
      return new WrappedEvmAccount(track(new UpdateTrackingAccount<>(origin)));
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

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    if (world instanceof WorldUpdater) {
      return Optional.of((WorldUpdater) world);
    } else {
      return Optional.empty();
    }
  }

  /**
   * The accounts modified in this updater.
   *
   * @return The accounts modified in this updater.
   */
  protected Collection<UpdateTrackingAccount<A>> getUpdatedAccounts() {
    return updatedAccounts.values();
  }

  /**
   * The accounts deleted as part of this updater.
   *
   * @return The accounts deleted as part of this updater.
   */
  protected Collection<Address> getDeletedAccounts() {
    return deletedAccounts;
  }

  public static class StackedUpdater<W extends WorldView, A extends Account>
      extends AbstractWorldUpdater<AbstractWorldUpdater<W, A>, UpdateTrackingAccount<A>> {

    StackedUpdater(final AbstractWorldUpdater<W, A> world) {
      super(world, new CodeCache());
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
    public Collection<? extends Account> getTouchedAccounts() {
      return new ArrayList<>(getUpdatedAccounts());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return new ArrayList<>(getDeletedAccounts());
    }

    @Override
    public void revert() {
      getDeletedAccounts().clear();
      getUpdatedAccounts().clear();
    }

    @Override
    public void commit() {
      final AbstractWorldUpdater<W, A> wrapped = wrappedWorldView();
      // Our own updates should apply on top of the updates we're stacked on top, so our deletions
      // may kill some of "their" updates, and our updates may review some of the account "they"
      // deleted.
      getDeletedAccounts().forEach(wrapped.updatedAccounts::remove);
      getDeletedAccounts().stream().map(a -> get(a)).forEach(super.codeCache::invalidate);
      getUpdatedAccounts().forEach(a -> wrapped.deletedAccounts.remove(a.getAddress()));

      // Then push our deletes and updates to the stacked ones.
      wrapped.deletedAccounts.addAll(getDeletedAccounts());

      for (final UpdateTrackingAccount<UpdateTrackingAccount<A>> update : getUpdatedAccounts()) {
        UpdateTrackingAccount<A> existing = wrapped.updatedAccounts.get(update.getAddress());
        if (existing == null) {
          // If we don't track this account, it's either a new one or getForMutation above had
          // created a tracker to satisfy the type system above and we can reuse that now.
          existing = update.getWrappedAccount();
          if (existing == null) {
            // Brand new account, create our own version
            existing = new UpdateTrackingAccount<>(update.getAddress());
          }
          wrapped.updatedAccounts.put(existing.getAddress(), existing);
        }
        existing.setNonce(update.getNonce());
        existing.setBalance(update.getBalance());
        if (update.codeWasUpdated()) {
          existing.setCode(update.getCode());
        }
        if (update.getStorageWasCleared()) {
          existing.clearStorage();
        }
        update.getUpdatedStorage().forEach(existing::setStorageValue);
      }
    }

    public void markTransactionBoundary() {
      getUpdatedAccounts().forEach(UpdateTrackingAccount::markTransactionBoundary);
    }
  }

  protected void reset() {
    updatedAccounts.clear();
    deletedAccounts.clear();
  }
}
