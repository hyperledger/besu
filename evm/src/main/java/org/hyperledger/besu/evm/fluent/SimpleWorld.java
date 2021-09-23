/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.fluent;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimpleWorld implements WorldUpdater {

  SimpleWorld parent;
  Map<Address, SimpleAccount> accounts = new HashMap<>();

  public SimpleWorld() {
    this(null);
  }

  public SimpleWorld(final SimpleWorld parent) {
    this.parent = parent;
  }

  @Override
  public WorldUpdater updater() {
    return new SimpleWorld(this);
  }

  @Override
  public Account get(final Address address) {
    if (accounts.containsKey(address)) {
      return accounts.get(address);
    } else if (parent != null) {
      return parent.get(address);
    } else {
      return null;
    }
  }

  @Override
  public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
    SimpleAccount account = new SimpleAccount(address, nonce, balance);
    accounts.put(address, account);
    return account;
  }

  @Override
  public EvmAccount getAccount(final Address address) {
    if (accounts.containsKey(address)) {
      return accounts.get(address);
    } else if (parent != null) {
      return parent.getAccount(address);
    } else {
      return null;
    }
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
        .collect(Collectors.toList());
  }

  @Override
  public void revert() {
    accounts = new HashMap<>();
  }

  @Override
  public void commit() {
    parent.accounts.putAll(accounts);
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.empty();
  }
}
