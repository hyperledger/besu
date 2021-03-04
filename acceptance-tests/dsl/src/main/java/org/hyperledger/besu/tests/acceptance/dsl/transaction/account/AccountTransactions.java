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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.account;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class AccountTransactions {

  private final Accounts accounts;

  public AccountTransactions(final Accounts accounts) {
    this.accounts = accounts;
  }

  public TransferTransaction createTransfer(final Account recipient, final int amount) {
    return createTransfer(recipient, amount, 2018L);
  }

  public TransferTransaction createTransfer(
      final Account recipient, final int amount, final long chainId) {
    return createTransfer(accounts.getPrimaryBenefactor(), recipient, amount, chainId);
  }

  public TransferTransaction createTransfer(final Account recipient, final Amount amount) {
    return createTransfer(accounts.getPrimaryBenefactor(), recipient, amount, 2018L);
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final int amount, final long chainId) {
    return new TransferTransactionBuilder()
        .sender(sender)
        .recipient(recipient)
        .amount(Amount.ether(amount))
        .chainId(chainId)
        .build();
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final Amount amount, final long chainId) {
    return new TransferTransactionBuilder()
        .sender(sender)
        .recipient(recipient)
        .amount(amount)
        .chainId(chainId)
        .build();
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final int amount, final BigInteger nonce) {
    return createTransfer(sender, recipient, amount, nonce, 2018L);
  }

  public TransferTransaction createTransfer(
      final Account sender,
      final Account recipient,
      final int amount,
      final BigInteger nonce,
      final long chainId) {
    return new TransferTransactionBuilder()
        .sender(sender)
        .recipient(recipient)
        .amount(Amount.ether(amount))
        .nonce(nonce)
        .chainId(chainId)
        .build();
  }

  public TransferTransactionSet createIncrementalTransfers(
      final Account sender, final Account recipient, final int etherAmount) {
    return createIncrementalTransfers(sender, recipient, etherAmount, 2018L);
  }

  public TransferTransactionSet createIncrementalTransfers(
      final Account sender, final Account recipient, final int etherAmount, final long chainId) {
    final List<TransferTransaction> transfers = new ArrayList<>();
    final TransferTransactionBuilder transferOneEther =
        new TransferTransactionBuilder()
            .sender(sender)
            .recipient(recipient)
            .chainId(chainId)
            .amount(Amount.ether(1));

    for (int i = 1; i <= etherAmount; i++) {
      transfers.add(transferOneEther.build());
    }

    return new TransferTransactionSet(transfers);
  }
}
