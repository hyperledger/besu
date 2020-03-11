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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.eth;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;

import java.math.BigInteger;

import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;

public class EthTransactions {

  public EthGetWorkTransaction getWork() {
    return new EthGetWorkTransaction();
  }

  public EthBlockNumberTransaction blockNumber() {
    return new EthBlockNumberTransaction();
  }

  public EthGetBlockTransaction block() {
    return block(DefaultBlockParameterName.LATEST);
  }

  public EthGetBlockTransaction block(final DefaultBlockParameter blockParameter) {
    return new EthGetBlockTransaction(blockParameter, false);
  }

  public EthGetBalanceTransaction getBalance(final Account account) {
    return new EthGetBalanceTransaction(account);
  }

  public EthGetBalanceAtBlockTransaction getBalanceAtBlock(
      final Account account, final BigInteger block) {
    return new EthGetBalanceAtBlockTransaction(account, block);
  }

  public EthAccountsTransaction accounts() {
    return new EthAccountsTransaction();
  }

  public EthGetTransactionReceiptTransaction getTransactionReceipt(final String transactionHash) {
    return new EthGetTransactionReceiptTransaction(transactionHash);
  }

  public EthSendRawTransactionTransaction sendRawTransaction(final String transactionData) {
    return new EthSendRawTransactionTransaction(transactionData);
  }

  public EthGetTransactionCountTransaction getTransactionCount(final String accountAddress) {
    return new EthGetTransactionCountTransaction(accountAddress);
  }

  public EthGetTransactionReceiptWithRevertReason getTransactionReceiptWithRevertReason(
      final String transactionHash) {
    return new EthGetTransactionReceiptWithRevertReason(transactionHash);
  }

  public EthMiningTransaction mining() {
    return new EthMiningTransaction();
  }

  public EthNewPendingTransactionFilterTransaction newPendingTransactionsFilter() {
    return new EthNewPendingTransactionFilterTransaction();
  }

  public EthFilterChangesTransaction filterChanges(final BigInteger filterId) {
    return new EthFilterChangesTransaction(filterId);
  }
}
