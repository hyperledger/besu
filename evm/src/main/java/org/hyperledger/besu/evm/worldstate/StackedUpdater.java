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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The Stacked updater.
 *
 * @param <W> the WorldView type parameter
 * @param <A> the Account type parameter
 */
public class StackedUpdater<W extends WorldView, A extends Account>
    extends AbstractWorldUpdater<AbstractWorldUpdater<W, A>, UpdateTrackingAccount<A>> {

  /**
   * Instantiates a new Stacked updater.
   *
   * @param world the world
   * @param evmConfiguration the EVM Configuration parameters
   */
  public StackedUpdater(
      final AbstractWorldUpdater<W, A> world, final EvmConfiguration evmConfiguration) {
    super(world, evmConfiguration);
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

  /** Mark transaction boundary. */
  @Override
  public void markTransactionBoundary() {
    getUpdatedAccounts().forEach(UpdateTrackingAccount::markTransactionBoundary);
  }
}
