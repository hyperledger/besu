/*
 * Copyright contributors to Besu.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode;
import org.hyperledger.besu.evm.toy.ToyAccount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JournaledUpdaterTest {

  private static class SimpleWorldView implements WorldView {
    final Map<Address, ToyAccount> accounts = new HashMap<>();

    @Override
    public Account get(final Address address) {
      return accounts.get(address);
    }
  }

  private static class SimpleWorldUpdater
      extends AbstractWorldUpdater<SimpleWorldView, ToyAccount> {

    SimpleWorldUpdater(final SimpleWorldView world) {
      super(world, new EvmConfiguration(0L, WorldUpdaterMode.JOURNALED, true));
    }

    @Override
    protected ToyAccount getForMutation(final Address address) {
      return wrappedWorldView().accounts.get(address);
    }

    @Override
    public void commit() {
      deletedAccounts.forEach(wrappedWorldView().accounts::remove);
      reset();
    }

    @Override
    public void revert() {
      reset();
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      return new ArrayList<>(updatedAccounts.values());
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return new ArrayList<>(deletedAccounts);
    }
  }

  @Test
  void accountGettersAcrossNestedUpdaters() {
    final SimpleWorldView worldView = new SimpleWorldView();
    final SimpleWorldUpdater root = new SimpleWorldUpdater(worldView);

    final WorldUpdater updaterA = root.updater();
    final Address address = Address.fromHexString("0x1234");

    final MutableAccount accountA = updaterA.createAccount(address, 0, Wei.ZERO);
    accountA.setNonce(1L);

    final WorldUpdater updaterB = updaterA.updater();
    final MutableAccount accountB = updaterB.getAccount(address);
    accountB.setNonce(2L);

    final WorldUpdater updaterC = updaterB.updater();
    final MutableAccount accountC = updaterC.getAccount(address);
    accountC.setNonce(3L);

    assertThat(updaterA.get(address).getNonce()).isEqualTo(1L);
    assertThat(updaterB.get(address).getNonce()).isEqualTo(2L);
    assertThat(updaterC.get(address).getNonce()).isEqualTo(3L);

    final MutableAccount mutableAccount = updaterC.getAccount(address);
    mutableAccount.setNonce(4L);

    assertThat(updaterA.get(address).getNonce()).isEqualTo(1L);
    assertThat(updaterB.get(address).getNonce()).isEqualTo(2L);
    assertThat(updaterC.get(address).getNonce()).isEqualTo(4L);

    assertThat(updaterA.getAccount(address).getNonce()).isEqualTo(4L);
    assertThat(updaterB.getAccount(address).getNonce()).isEqualTo(4L);
    assertThat(updaterC.getAccount(address).getNonce()).isEqualTo(4L);
  }

  @Test
  void deletionsAreIsolatedToChildUpdater() {
    final SimpleWorldView worldView = new SimpleWorldView();
    final SimpleWorldUpdater root = new SimpleWorldUpdater(worldView);

    final WorldUpdater updaterA = root.updater();
    final Address address = Address.fromHexString("0x1234");

    final MutableAccount accountA = updaterA.createAccount(address, 0, Wei.ZERO);
    accountA.setNonce(1L);

    final WorldUpdater updaterB = updaterA.updater();
    final MutableAccount accountB = updaterB.getAccount(address);
    accountB.setNonce(2L);

    final WorldUpdater updaterC = updaterB.updater();

    updaterC.deleteAccount(address);

    assertThat(updaterA.get(address).getNonce()).isEqualTo(1L);
    assertThat(updaterB.get(address).getNonce()).isEqualTo(2L);
    assertThat(updaterC.get(address)).isNull();

    assertThat(updaterA.getAccount(address)).isNull();
    assertThat(updaterB.getAccount(address)).isNull();
    assertThat(updaterC.getAccount(address)).isNull();
  }
}
