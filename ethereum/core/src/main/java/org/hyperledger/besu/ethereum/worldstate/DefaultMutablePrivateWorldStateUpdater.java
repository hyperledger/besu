/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.ReadOnlyMutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;

import java.util.Collection;

public class DefaultMutablePrivateWorldStateUpdater implements WorldUpdater {

  private final WorldUpdater publicWorldUpdater;
  private final WorldUpdater privateWorldUpdater;

  public DefaultMutablePrivateWorldStateUpdater(
      final WorldUpdater publicWorldUpdater, final WorldUpdater privateWorldUpdater) {
    this.publicWorldUpdater = publicWorldUpdater;
    this.privateWorldUpdater = privateWorldUpdater;
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    return privateWorldUpdater.createAccount(address);
  }

  @Override
  public MutableAccount createAccount(final Address address) {
    return privateWorldUpdater.createAccount(address);
  }

  @Override
  public MutableAccount getOrCreate(final Address address) {
    return privateWorldUpdater.getOrCreate(address);
  }

  @Override
  public MutableAccount getMutable(final Address address) {
    final MutableAccount privateAccount = privateWorldUpdater.getMutable(address);
    if (privateAccount != null && !privateAccount.isEmpty()) {
      return privateAccount;
    }
    final MutableAccount publicAccount = publicWorldUpdater.getMutable(address);
    if (publicAccount != null && !publicAccount.isEmpty()) {
      return new ReadOnlyMutableAccount(publicAccount);
    }
    return null;
  }

  @Override
  public void deleteAccount(final Address address) {
    privateWorldUpdater.deleteAccount(address);
  }

  @Override
  public Collection<Account> getTouchedAccounts() {
    return privateWorldUpdater.getTouchedAccounts();
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
}
