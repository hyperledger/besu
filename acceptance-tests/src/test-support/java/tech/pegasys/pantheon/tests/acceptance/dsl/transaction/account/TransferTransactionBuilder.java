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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account;

import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.blockchain.Amount;

import java.math.BigInteger;

public class TransferTransactionBuilder {

  private Account sender;
  private Account recipient;
  private Amount transferAmount;
  private Amount gasPrice;
  private BigInteger nonce;

  public TransferTransactionBuilder sender(final Account sender) {
    this.sender = sender;
    validateSender();
    return this;
  }

  public TransferTransactionBuilder recipient(final Account recipient) {
    this.recipient = recipient;
    return this;
  }

  public TransferTransactionBuilder amount(final Amount transferAmount) {
    this.transferAmount = transferAmount;
    validateTransferAmount();
    return this;
  }

  public TransferTransactionBuilder nonce(final BigInteger nonce) {
    this.nonce = nonce;
    return this;
  }

  public TransferTransactionBuilder gasPrice(final Amount gasPrice) {
    this.gasPrice = gasPrice;
    return this;
  }

  public TransferTransaction build() {
    validateSender();
    validateTransferAmount();
    return new TransferTransaction(sender, recipient, transferAmount, gasPrice, nonce);
  }

  private void validateSender() {
    if (sender == null) {
      throw new IllegalArgumentException("NULL sender is not allowed.");
    }
  }

  private void validateTransferAmount() {
    if (transferAmount == null) {
      throw new IllegalArgumentException("NULL transferAmount is not allowed.");
    }
  }
}
