/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction;

import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Accounts;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account.TransferTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account.TransferTransactionSet;

import java.util.ArrayList;
import java.util.List;

import org.web3j.utils.Convert.Unit;

public class Transactions {

  private final Accounts accounts;

  public Transactions(final Accounts accounts) {
    this.accounts = accounts;
  }

  public TransferTransaction createTransfer(final Account recipient, final int amount) {
    return createTransfer(accounts.getPrimaryBenefactor(), recipient, amount);
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final int amount) {
    return new TransferTransaction(sender, recipient, String.valueOf(amount), Unit.ETHER);
  }

  public TransferTransactionSet createIncrementalTransfers(
      final Account sender, final Account recipient, final int etherAmount) {
    final List<TransferTransaction> transfers = new ArrayList<>();

    for (int i = 1; i <= etherAmount; i++) {
      transfers.add(new TransferTransaction(sender, recipient, "1", Unit.ETHER));
    }

    return new TransferTransactionSet(transfers);
  }
}
