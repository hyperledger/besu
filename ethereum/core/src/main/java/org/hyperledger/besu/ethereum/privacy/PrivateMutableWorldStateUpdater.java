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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collection;
import java.util.Optional;

// This class uses a public WorldUpdater and a private WorldUpdater to provide a
// MutableWorldStateUpdater that can read and write from the private world state and can read from
// the public world state, but cannot write to it.
public class PrivateMutableWorldStateUpdater implements WorldUpdater {

  protected final WorldUpdater publicWorldUpdater;
  protected final WorldUpdater privateWorldUpdater;

  public PrivateMutableWorldStateUpdater(
      final WorldUpdater publicWorldUpdater, final WorldUpdater privateWorldUpdater) {
    this.publicWorldUpdater = publicWorldUpdater;
    this.privateWorldUpdater = privateWorldUpdater;
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    return privateWorldUpdater.createAccount(address, nonce, balance);
  }

  @Override
  public MutableAccount createAccount(final Address address) {
    return privateWorldUpdater.createAccount(address);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    final MutableAccount privateAccount = privateWorldUpdater.getAccount(address);
    if (privateAccount != null && !privateAccount.isEmpty()) {
      return privateAccount;
    }
    final MutableAccount publicAccount = publicWorldUpdater.getAccount(address);
    if (publicAccount != null && !publicAccount.isEmpty()) {
      publicAccount.becomeImmutable();
      return publicAccount;
    }
    return privateAccount;
  }

  @Override
  public void deleteAccount(final Address address) {
    privateWorldUpdater.deleteAccount(address);
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return privateWorldUpdater.getTouchedAccounts();
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return privateWorldUpdater.getDeletedAccountAddresses();
  }

  @Override
  public void revert() {
    privateWorldUpdater.revert();
  }

  @Override
  public void commit() {
    privateWorldUpdater.commit();
  }

  @Override
  public Account get(final Address address) {
    final Account privateAccount = privateWorldUpdater.get(address);
    if (privateAccount != null && !privateAccount.isEmpty()) {
      return privateAccount;
    }
    return publicWorldUpdater.get(address);
  }

  @Override
  public WorldUpdater updater() {
    return this;
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return privateWorldUpdater.parentUpdater();
  }
}
