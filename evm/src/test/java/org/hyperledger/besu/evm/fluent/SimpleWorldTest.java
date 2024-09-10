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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleWorldTest {
  private static final Address ADDRESS1 = Address.fromHexString("0x0");
  private static final Address ADDRESS2 = Address.fromHexString("0x1");
  private static final Address ADDRESS3 = Address.fromHexString("0x2");

  private SimpleWorld simpleWorld;

  @BeforeEach
  void setUp() {
    simpleWorld = new SimpleWorld();
  }

  @Test
  void get_noAccounts() {
    assertThat(simpleWorld.get(ADDRESS1)).isNull();
  }

  @Test
  void get_noAccountsWithParent() {
    WorldUpdater childUpdater = simpleWorld.updater();
    assertThat(childUpdater.get(ADDRESS1)).isNull();
  }

  @Test
  void createAccount_cannotCreateIfExists() {
    simpleWorld.createAccount(ADDRESS1);
    assertThatThrownBy(() -> simpleWorld.createAccount(ADDRESS1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void get_createdAccountExistsInParent() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    WorldUpdater childUpdater = simpleWorld.updater();
    assertThat(childUpdater.get(ADDRESS1)).isEqualTo(account);
  }

  @Test
  void get_createdAccountExistsInMultiParent() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    WorldUpdater childUpdater = simpleWorld.updater();
    childUpdater = childUpdater.updater();
    childUpdater = childUpdater.updater();
    childUpdater = childUpdater.updater();
    assertThat(childUpdater.get(ADDRESS1)).isEqualTo(account);
  }

  @Test
  void get_createdAccountExists() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    assertThat(simpleWorld.get(ADDRESS1)).isEqualTo(account);
  }

  @Test
  void get_createdAccountDeleted() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.deleteAccount(ADDRESS1);
    assertThat(simpleWorld.get(ADDRESS1)).isNull();
  }

  @Test
  void get_revertRemovesAllAccounts() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.revert();
    assertThat(simpleWorld.get(ADDRESS1)).isNull();
    assertThat(simpleWorld.get(ADDRESS2)).isNull();
    assertThat(simpleWorld.get(ADDRESS3)).isNull();
  }

  @Test
  void get_commitKeepsAllAccounts() {
    MutableAccount acc1 = simpleWorld.createAccount(ADDRESS1);
    MutableAccount acc2 = simpleWorld.createAccount(ADDRESS2);
    MutableAccount acc3 = simpleWorld.createAccount(ADDRESS3);
    simpleWorld.commit();
    assertThat(simpleWorld.get(ADDRESS1)).isEqualTo(acc1);
    assertThat(simpleWorld.get(ADDRESS2)).isEqualTo(acc2);
    assertThat(simpleWorld.get(ADDRESS3)).isEqualTo(acc3);
  }

  @Test
  void get_createdAccountDeletedInChild() {
    simpleWorld.createAccount(ADDRESS1);
    WorldUpdater childUpdater = simpleWorld.updater();
    childUpdater.deleteAccount(ADDRESS1);
    assertThat(childUpdater.get(ADDRESS1)).isNull();
  }

  @Test
  void getAccount_noAccounts() {
    assertThat(simpleWorld.getAccount(ADDRESS1)).isNull();
  }

  @Test
  void getAccount_noAccountsWithParent() {
    WorldUpdater childUpdater = simpleWorld.updater();
    assertThat(childUpdater.getAccount(ADDRESS1)).isNull();
  }

  @Test
  void getAccount_createdAccountExistsInParent() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    account.setStorageValue(UInt256.MAX_VALUE, UInt256.ONE);
    WorldUpdater childUpdater = simpleWorld.updater();
    assertThat(childUpdater.getAccount(ADDRESS1).getOriginalStorageValue(UInt256.MAX_VALUE))
        .isEqualTo(UInt256.ONE);
  }

  @Test
  void getAccount_createdAccountExistsInMultiParent() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    account.setStorageValue(UInt256.MAX_VALUE, UInt256.ONE);
    WorldUpdater childUpdater = simpleWorld.updater();
    childUpdater = childUpdater.updater();
    childUpdater = childUpdater.updater();
    childUpdater = childUpdater.updater();
    assertThat(childUpdater.getAccount(ADDRESS1).getOriginalStorageValue(UInt256.MAX_VALUE))
        .isEqualTo(UInt256.ONE);
  }

  @Test
  void getAccount_createdAccountExists() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    assertThat(simpleWorld.getAccount(ADDRESS1)).isEqualTo(account);
  }

  @Test
  void getAccount_createdAccountDeleted() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.deleteAccount(ADDRESS1);
    assertThat(simpleWorld.getAccount(ADDRESS1)).isNull();
  }

  @Test
  void getAccount_revertRemovesAllAccounts() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.revert();
    assertThat(simpleWorld.getAccount(ADDRESS1)).isNull();
    assertThat(simpleWorld.getAccount(ADDRESS2)).isNull();
    assertThat(simpleWorld.getAccount(ADDRESS3)).isNull();
  }

  @Test
  void getAccount_commitKeepsAllAccounts() {
    MutableAccount acc1 = simpleWorld.createAccount(ADDRESS1);
    MutableAccount acc2 = simpleWorld.createAccount(ADDRESS2);
    MutableAccount acc3 = simpleWorld.createAccount(ADDRESS3);
    simpleWorld.commit();
    assertThat(simpleWorld.getAccount(ADDRESS1)).isEqualTo(acc1);
    assertThat(simpleWorld.getAccount(ADDRESS2)).isEqualTo(acc2);
    assertThat(simpleWorld.getAccount(ADDRESS3)).isEqualTo(acc3);
  }

  @Test
  void getAccount_createdAccountDeletedInChild() {
    simpleWorld.createAccount(ADDRESS1);
    WorldUpdater childUpdater = simpleWorld.updater();
    childUpdater.deleteAccount(ADDRESS1);
    assertThat(childUpdater.getAccount(ADDRESS1)).isNull();
  }

  @Test
  void getTouchedAccounts_createdAccounts() {
    Account acc1 = simpleWorld.createAccount(ADDRESS1);
    Account acc2 = simpleWorld.createAccount(ADDRESS2);
    Account acc3 = simpleWorld.createAccount(ADDRESS3);
    assertThat(simpleWorld.getTouchedAccounts().toArray())
        .containsExactlyInAnyOrder(acc1, acc2, acc3);
  }

  @Test
  void getTouchedAccounts_revertedAccounts() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.revert();
    assertThat(simpleWorld.getTouchedAccounts()).isEmpty();
  }

  @Test
  void getTouchedAccounts_createdAndDeletedAccounts() {
    Account acc1 = simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    Account acc3 = simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS2);
    assertThat(simpleWorld.getTouchedAccounts().toArray()).containsExactlyInAnyOrder(acc1, acc3);
  }

  @Test
  void getTouchedAccounts_allDeletedAccounts() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS1);
    simpleWorld.deleteAccount(ADDRESS2);
    simpleWorld.deleteAccount(ADDRESS3);
    assertThat(simpleWorld.getTouchedAccounts()).isEmpty();
  }

  @Test
  void getTouchedAccounts_createdAndCommittedAccounts() {
    Account acc1 = simpleWorld.createAccount(ADDRESS1);
    Account acc2 = simpleWorld.createAccount(ADDRESS2);
    Account acc3 = simpleWorld.createAccount(ADDRESS3);
    simpleWorld.commit();
    assertThat(simpleWorld.getTouchedAccounts().toArray())
        .containsExactlyInAnyOrder(acc1, acc2, acc3);
  }

  @Test
  void getDeletedAccountAddresses_singleDeleted() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS2);
    assertThat(simpleWorld.getDeletedAccountAddresses().toArray()).containsExactly(ADDRESS2);
  }

  @Test
  void getDeletedAccountAddresses_allDeleted() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS1);
    simpleWorld.deleteAccount(ADDRESS2);
    simpleWorld.deleteAccount(ADDRESS3);
    assertThat(simpleWorld.getDeletedAccountAddresses().toArray())
        .containsExactlyInAnyOrder(ADDRESS1, ADDRESS2, ADDRESS3);
  }

  @Test
  void getDeletedAccountAddresses_allDeletedThenRevert() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS1);
    simpleWorld.deleteAccount(ADDRESS2);
    simpleWorld.deleteAccount(ADDRESS3);
    simpleWorld.revert();
    assertThat(simpleWorld.getDeletedAccountAddresses()).isEmpty();
  }

  @Test
  void getDeletedAccountAddresses_allDeletedThenCommit() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS1);
    simpleWorld.deleteAccount(ADDRESS2);
    simpleWorld.deleteAccount(ADDRESS3);
    simpleWorld.commit();
    assertThat(simpleWorld.getDeletedAccountAddresses().toArray())
        .containsExactlyInAnyOrder(ADDRESS1, ADDRESS2, ADDRESS3);
  }

  @Test
  void commit_deletedAccountNoNPEs() {
    simpleWorld.createAccount(ADDRESS1);
    simpleWorld.createAccount(ADDRESS2);
    simpleWorld.createAccount(ADDRESS3);
    simpleWorld.deleteAccount(ADDRESS1);
    simpleWorld.commit();
  }

  @Test
  void commit_onlyCommitsNewAccountsToDirectParent() {
    WorldUpdater simpleWorldLevel1 = simpleWorld.updater();
    WorldUpdater simpleWorldLevel2 = simpleWorldLevel1.updater();
    MutableAccount createdAccount = simpleWorldLevel2.createAccount(ADDRESS1);
    simpleWorldLevel2.commit();
    assertThat(simpleWorldLevel1.getTouchedAccounts().toArray()).containsExactly(createdAccount);
    assertThat(simpleWorld.getTouchedAccounts()).isEmpty();
  }

  @Test
  void commit_onlyCommitsDeletedAccountsToDirectParent() {
    WorldUpdater simpleWorldLevel1 = simpleWorld.updater();
    WorldUpdater simpleWorldLevel2 = simpleWorldLevel1.updater();
    simpleWorldLevel2.createAccount(ADDRESS2);
    simpleWorldLevel2.deleteAccount(ADDRESS2);
    simpleWorldLevel2.commit();
    assertThat(simpleWorldLevel1.getDeletedAccountAddresses().toArray()).containsExactly(ADDRESS2);
    assertThat(simpleWorld.getDeletedAccountAddresses()).isEmpty();
  }

  @Test
  void commit_accountsReflectChangesAfterCommit() {
    MutableAccount account = simpleWorld.createAccount(ADDRESS1);
    account.setStorageValue(UInt256.MAX_VALUE, UInt256.ONE);
    WorldUpdater simpleWorldUpdater = simpleWorld.updater();

    account = simpleWorldUpdater.getAccount(ADDRESS1);
    account.setStorageValue(UInt256.MAX_VALUE, UInt256.valueOf(22L));
    simpleWorldUpdater.commit();

    assertThat(simpleWorldUpdater.get(ADDRESS1).getStorageValue(UInt256.MAX_VALUE))
        .isEqualTo(simpleWorldUpdater.get(ADDRESS1).getOriginalStorageValue(UInt256.MAX_VALUE));
  }
}
