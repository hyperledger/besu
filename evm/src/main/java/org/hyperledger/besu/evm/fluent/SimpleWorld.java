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
package org.hyperledger.besu.evm.fluent;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.AuthorizedCodeService;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The Simple world. */
public class SimpleWorld implements WorldUpdater {

  /** The Parent. */
  SimpleWorld parent;

  /** The Accounts. */
  Map<Address, SimpleAccount> accounts = new HashMap<>();

  private AuthorizedCodeService authorizedCodeService;

  /** Instantiates a new Simple world. */
  public SimpleWorld() {
    this(null);
  }

  /**
   * Instantiates a new Simple world.
   *
   * @param parent the parent
   */
  public SimpleWorld(final SimpleWorld parent) {
    this.parent = parent;
    this.authorizedCodeService = new AuthorizedCodeService();
  }

  @Override
  public WorldUpdater updater() {
    return new SimpleWorld(this);
  }

  @Override
  public Account get(final Address address) {
    if (accounts.containsKey(address)) {
      return authorizedCodeService.processAccount(this, accounts.get(address), address);
    } else if (parent != null) {
      return authorizedCodeService.processAccount(this, parent.get(address), address);
    } else {
      return authorizedCodeService.processAccount(this, null, address);
    }
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    if (getAccount(address) != null) {
      throw new IllegalStateException("Cannot create an account when one already exists");
    }
    SimpleAccount account = new SimpleAccount(address, nonce, balance);
    accounts.put(address, account);
    return authorizedCodeService.processMutableAccount(this, account, address);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    SimpleAccount account = accounts.get(address);
    if (account != null) {
      return authorizedCodeService.processMutableAccount(this, account, address);
    }
    Account parentAccount = parent == null ? null : parent.getAccount(address);
    if (parentAccount != null) {
      account =
          new SimpleAccount(
              parentAccount,
              parentAccount.getAddress(),
              parentAccount.getNonce(),
              parentAccount.getBalance(),
              parentAccount.getCode());
      accounts.put(address, account);
      return authorizedCodeService.processMutableAccount(this, account, address);
    }
    return authorizedCodeService.processMutableAccount(this, null, address);
  }

  @Override
  public void deleteAccount(final Address address) {
    accounts.put(address, null);
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return accounts.values();
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return accounts.entrySet().stream()
        .filter(e -> e.getValue() == null)
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public void revert() {
    accounts = new HashMap<>();
  }

  @Override
  public void commit() {
    accounts.forEach(
        (address, account) -> {
          if (!account.updateParent()) {
            parent.accounts.put(address, account);
          }
        });
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.ofNullable(parent);
  }

  @Override
  public void setAuthorizedCodeService(final AuthorizedCodeService authorizedCodeService) {
    this.authorizedCodeService = authorizedCodeService;
  }
}
