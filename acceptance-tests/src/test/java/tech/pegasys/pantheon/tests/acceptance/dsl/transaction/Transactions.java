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
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaGetTransactionReceiptTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaSendRawTransactionTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthGetTransactionCountTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthGetTransactionReceiptTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermAddAccountsToWhitelistTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermAddNodeTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermGetAccountsWhitelistTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermGetNodesWhitelistTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermRemoveAccountsFromWhitelistTransaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermRemoveNodeTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.web3j.tx.Contract;
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

  public TransferTransaction createTransfer(
      final Account sender, final Account recipient, final int amount, final BigInteger nonce) {
    return new TransferTransaction(sender, recipient, String.valueOf(amount), Unit.ETHER, nonce);
  }

  public EeaSendRawTransactionTransaction createPrivateRawTransaction(
      final String signedRawPrivateTransaction) {
    return new EeaSendRawTransactionTransaction(signedRawPrivateTransaction);
  }

  public TransferTransactionSet createIncrementalTransfers(
      final Account sender, final Account recipient, final int etherAmount) {
    final List<TransferTransaction> transfers = new ArrayList<>();

    for (int i = 1; i <= etherAmount; i++) {
      transfers.add(new TransferTransaction(sender, recipient, "1", Unit.ETHER));
    }

    return new TransferTransactionSet(transfers);
  }

  public <T extends Contract> DeploySmartContractTransaction<T> createSmartContract(
      final Class<T> clazz) {
    return new DeploySmartContractTransaction<>(clazz);
  }

  public PermAddAccountsToWhitelistTransaction addAccountsToWhitelist(final String... accounts) {
    return new PermAddAccountsToWhitelistTransaction(Arrays.asList(accounts));
  }

  public PermRemoveAccountsFromWhitelistTransaction removeAccountsFromWhitelist(
      final String... accounts) {
    return new PermRemoveAccountsFromWhitelistTransaction(Arrays.asList(accounts));
  }

  public PermGetAccountsWhitelistTransaction getAccountsWhiteList() {
    return new PermGetAccountsWhitelistTransaction();
  }

  public EthGetTransactionCountTransaction getTransactionCount(final String accountAddress) {
    return new EthGetTransactionCountTransaction(accountAddress);
  }

  public EthGetTransactionReceiptTransaction getTransactionReceipt(final String transactionHash) {
    return new EthGetTransactionReceiptTransaction(transactionHash);
  }

  public EeaGetTransactionReceiptTransaction getPrivateTransactionReceipt(
      final String transactionHash, final String publicKey) {
    return new EeaGetTransactionReceiptTransaction(transactionHash, publicKey);
  }

  public PermAddNodeTransaction addNodesToWhitelist(final List<String> enodeList) {
    return new PermAddNodeTransaction(enodeList);
  }

  public PermRemoveNodeTransaction removeNodesFromWhitelist(final List<String> enodeList) {
    return new PermRemoveNodeTransaction(enodeList);
  }

  public PermGetNodesWhitelistTransaction getNodesWhiteList() {
    return new PermGetNodesWhitelistTransaction();
  }
}
