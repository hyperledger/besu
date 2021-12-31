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

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class AccountTransactions {

  private static final Amount DEFAULT_GAS_PRICE = Amount.wei(BigInteger.valueOf(10000000000L));
  private final Accounts accounts;

  public AccountTransactions(final Accounts accounts) {
    this.accounts = accounts;
  }

  public TransferTransaction createTransfer(final Account recipient, final int amount) {
    return createTransfer(accounts.getPrimaryBenefactor(), recipient, amount);
  }

  public TransferTransaction createTransfer(
      final Account recipient, final int amount, final SignatureAlgorithm signatureAlgorithm) {
    return createBuilder(accounts.getPrimaryBenefactor(), recipient, Amount.ether(amount))
        .setSignatureAlgorithm(signatureAlgorithm)
        .build();
  }

  public TransferTransaction createTransfer(
      final Account recipient, final int amount, final long chainId) {
    return createBuilder(accounts.getPrimaryBenefactor(), recipient, Amount.ether(amount))
        .chainId(chainId)
        .build();
  }

  public TransferTransaction createTransfer(final Account recipient, final Amount amount) {
    return createTransfer(accounts.getPrimaryBenefactor(), recipient, amount);
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final int amount) {
    return createBuilder(sender, recipient, Amount.ether(amount)).build();
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final Amount amount) {
    return createBuilder(sender, recipient, amount).build();
  }

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final int amount, final BigInteger nonce) {
    return createBuilder(sender, recipient, Amount.ether(amount)).nonce(nonce).build();
  }

  public TransferTransactionSet createIncrementalTransfers(
      final Account sender, final Account recipient, final int etherAmount) {
    final List<TransferTransaction> transfers = new ArrayList<>();
    final TransferTransactionBuilder transferOneEther =
        createBuilder(sender, recipient, Amount.ether(1));

    for (int i = 1; i <= etherAmount; i++) {
      transfers.add(transferOneEther.build());
    }

    return new TransferTransactionSet(transfers);
  }

  private TransferTransactionBuilder createBuilder(
      final Account sender, final Account recipient, final Amount amount) {
    return new TransferTransactionBuilder()
        .sender(sender)
        .recipient(recipient)
        .amount(amount)
        .gasPrice(DEFAULT_GAS_PRICE);
  }
}
