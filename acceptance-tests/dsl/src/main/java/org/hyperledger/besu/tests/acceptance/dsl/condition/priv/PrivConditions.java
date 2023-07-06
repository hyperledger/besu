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
package org.hyperledger.besu.tests.acceptance.dsl.condition.priv;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyTransactions;

import java.util.List;

public class PrivConditions {

  private final PrivacyTransactions transactions;

  public PrivConditions(final PrivacyTransactions transactions) {
    this.transactions = transactions;
  }

  public Condition getPrivacyPrecompileAddress(final Address precompileAddress) {
    return new PrivGetPrivacyPrecompileAddressSuccess(
        transactions.getPrivacyPrecompileAddress(), precompileAddress);
  }

  public Condition getPrivateTransaction(
      final Hash transactionHash, final PrivateTransaction privateTransaction) {
    return new PrivGetPrivateTransactionSuccess(
        transactions.getPrivateTransaction(transactionHash), privateTransaction);
  }

  public Condition getPrivateTransactionReturnsNull(final Hash transactionHash) {
    return new PrivGetPrivateTransactionReturnsNull(
        transactions.getPrivateTransaction(transactionHash));
  }

  public Condition createPrivacyGroup(
      final List<String> addresses,
      final String groupName,
      final String groupDescription,
      final String groupId) {
    return new PrivCreatePrivacyGroupSuccess(
        transactions.createPrivacyGroup(addresses, groupName, groupDescription), groupId);
  }

  public Condition deletePrivacyGroup(final String groupId) {
    return new PrivDeletePrivacyGroupSuccess(transactions.deletePrivacyGroup(groupId), groupId);
  }

  public Condition findPrivacyGroup(final int numGroups, final String... groupMembers) {
    return new PrivFindPrivacyGroupSuccess(transactions.findPrivacyGroup(groupMembers), numGroups);
  }

  public Condition eeaSendRawTransaction(final String transaction) {
    return new EeaSendRawTransactionSuccess(transactions.sendRawTransaction(transaction));
  }

  public Condition distributeRawTransaction(
      final String transactionRLP, final String enclaveResponseKey) {
    return new PrivDistributeRawTransactionSuccess(
        transactions.distributeRawTransaction(transactionRLP), enclaveResponseKey);
  }

  public Condition getTransactionCount(
      final String accountAddress,
      final String privacyGroupId,
      final int expectedTransactionCount) {
    return new PrivGetTransactionCountSuccess(
        transactions.getTransactionCount(accountAddress, privacyGroupId), expectedTransactionCount);
  }

  public Condition getEeaTransactionCount(
      final String accountAddress,
      final String privateFrom,
      final String[] privateFor,
      final int expectedTransactionCount) {
    return new PrivGetEeaTransactionCountSuccess(
        transactions.getEeaTransactionCount(accountAddress, privateFrom, privateFor),
        expectedTransactionCount);
  }

  public Condition getSuccessfulTransactionReceipt(final Hash transactionHash) {
    return new PrivGetExpectedSuccessfulTransactionReceipt(
        transactions.getTransactionReceipt(transactionHash));
  }

  public Condition getFailedTransactionReceipt(final Hash transactionHash) {
    return new PrivGetExpectedFailedTransactionReceipt(
        transactions.getTransactionReceipt(transactionHash));
  }

  public Condition getInvalidTransactionReceipt(final Hash transactionHash) {
    return new PrivGetExpectedInvalidTransactionReceipt(
        transactions.getTransactionReceipt(transactionHash));
  }

  public Condition multiTenancyValidationFail(
      final Transaction<?> transaction, final JsonRpcError error) {
    return new ExpectJsonRpcError(transaction, error);
  }
}
