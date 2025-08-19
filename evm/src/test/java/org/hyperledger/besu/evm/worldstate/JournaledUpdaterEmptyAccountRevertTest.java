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
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode;
import org.hyperledger.besu.evm.toy.ToyAccount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JournaledUpdaterEmptyAccountRevertTest {

  private static class SimpleWorldView implements WorldView {
    final Map<Address, ToyAccount> accounts = new HashMap<>();

    @Override
    public Account get(final Address address) {
      return accounts.get(address);
    }
  }

  private static class SimpleWorldUpdater
      extends AbstractWorldUpdater<SimpleWorldView, ToyAccount> {

    SimpleWorldUpdater(final SimpleWorldView world, final WorldUpdaterMode mode) {
      super(world, new EvmConfiguration(0L, mode));
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

  private static Collection<Address> emptyTouched(final WorldUpdater updater) {
    return updater.getTouchedAccounts().stream()
        .filter(Account::isEmpty)
        .map(Account::getAddress)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  static Stream<Arguments> modes() {
    return Stream.of(
        Arguments.of(WorldUpdaterMode.STACKED), Arguments.of(WorldUpdaterMode.JOURNALED));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void emptyAccountTouchedInRevertedFrameShouldPersist(final WorldUpdaterMode mode) {
    final SimpleWorldView worldView = new SimpleWorldView();
    final Address empty = Address.fromHexString("0x4242424242424242424242424242424242424242");
    worldView.accounts.put(empty, new ToyAccount(null, empty, 0L, Wei.ZERO, Bytes.EMPTY));

    final SimpleWorldUpdater root = new SimpleWorldUpdater(worldView, mode);
    final WorldUpdater frameA = root.updater();
    final WorldUpdater frameB = frameA.updater();

    frameB.getAccount(empty);

    final Collection<Address> emptyTouchedFrameA = emptyTouched(frameA);
    assertThat(emptyTouchedFrameA).isEmpty();

    final Collection<Address> emptyTouchedFrameB = emptyTouched(frameB);
    assertThat(emptyTouchedFrameB).isNotEmpty();

    frameB.revert();

    frameA.commit();
    root.commit();

    assertThat(worldView.accounts).containsKey(empty);
  }
}
