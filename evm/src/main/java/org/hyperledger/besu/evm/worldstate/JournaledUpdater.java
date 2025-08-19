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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Journaled updater.
 *
 * @param <W> the WorldView type parameter
 */
public class JournaledUpdater<W extends WorldView> implements WorldUpdater {

  final EvmConfiguration evmConfiguration;
  final WorldUpdater parentWorld;
  final AbstractWorldUpdater<W, ? extends MutableAccount> rootWorld;
  final UndoMap<Address, JournaledAccount> accounts;
  final UndoSet<Address> deleted;
  final HashSet<Address> touched;
  final long undoMark;

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
    if (world instanceof JournaledUpdater<?>) {
      JournaledUpdater<W> journaledUpdater = (JournaledUpdater<W>) world;
      accounts = journaledUpdater.accounts;
      deleted = journaledUpdater.deleted;
      touched = new HashSet<>();
      rootWorld = journaledUpdater.rootWorld;
    } else if (world instanceof AbstractWorldUpdater<?, ?>) {
      accounts = new UndoMap<>(new HashMap<>());
      deleted = UndoSet.of(new HashSet<>());
      touched = new HashSet<>();
      rootWorld = (AbstractWorldUpdater<W, ? extends MutableAccount>) world;
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
        .map(accounts::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return new ArrayList<>(deleted);
  }

  /**
   * Remove all changes done by this layer. Rollback to the state prior to the updater's changes.
   */
  protected void reset() {
    accounts.values().forEach(a -> a.undo(undoMark));
    accounts.undo(undoMark);
    deleted.undo(undoMark);
    touched.clear();
  }

  @Override
  public void revert() {
    reset();
  }

  @Override
  public void commit() {
    if (parentWorld instanceof JournaledUpdater<?> jw) {
      jw.touched.addAll(this.touched);
      return;
    }

    for (final JournaledAccount a : accounts.values()) {
      if (!deleted.contains(a.getAddress())) {
        if (a.getWrappedAccount() == null) {
          final MutableAccount pa =
              rootWorld.createAccount(a.getAddress(), a.getNonce(), a.getBalance());
          a.setWrappedAccount(pa);
        }
        a.commit();
      }
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
    touched.add(address);
    deleted.remove(address);
    return ja;
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    if (deleted.contains(address)) {
      return null;
    }
    // We may have updated it already, so check that first.
    final JournaledAccount existing = accounts.get(address);
    if (existing != null) {
      touched.add(address);
      return existing;
    }

    // Otherwise, get it from our wrapped view and create a new update tracker.
    final MutableAccount origin = rootWorld.getAccount(address);
    if (origin == null) {
      return null;
    } else {
      var newAccount = new JournaledAccount(origin);
      accounts.put(address, newAccount);
      touched.add(address);
      return newAccount;
    }
  }

  @Override
  public void deleteAccount(final Address address) {
    deleted.add(address);
    touched.remove(address);
  }

  @Override
  public Account get(final Address address) {
    final MutableAccount existing = accounts.get(address);
    if (existing != null && !deleted.contains(address)) {
      return existing;
    }
    if (deleted.contains(address)) {
      return null;
    }
    return rootWorld.get(address);
  }

  @Override
  public WorldUpdater updater() {
    return new JournaledUpdater<W>(this, evmConfiguration);
  }
}
