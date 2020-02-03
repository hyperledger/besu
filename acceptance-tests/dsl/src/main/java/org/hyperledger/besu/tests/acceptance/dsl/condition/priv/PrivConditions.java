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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyTransactions;

import java.util.List;

public class PrivConditions {

  private final PrivacyTransactions transactions;

  public PrivConditions(final PrivacyTransactions transactions) {
    this.transactions = transactions;
  }

  public Condition privGetPrivacyPrecompileAddressSuccess(final Address precompileAddress) {
    return new PrivGetPrivacyPrecompileAddressSuccess(
        transactions.getPrivacyPrecompileAddress(), precompileAddress);
  }

  public Condition privGetPrivateTransactionSuccess(
      final Hash transactionHash, final String privateFrom) {
    return new PrivGetPrivateTransactionSuccess(
        transactions.getPrivateTransaction(transactionHash), privateFrom);
  }

  public Condition privCreatePrivacyGroupSuccess(
      final List<String> addresses,
      final String groupName,
      final String groupDescription,
      final String groupId) {
    return new PrivCreatePrivacyGroupSuccess(
        transactions.createPrivacyGroup(addresses, groupName, groupDescription), groupId);
  }

  public Condition privDeletePrivacyGroupSuccess(final String groupId) {
    return new PrivDeletePrivacyGroupSuccess(transactions.deletePrivacyGroup(groupId), groupId);
  }

  public Condition privFindPrivacyGroupSuccess(final int numGroups, final String... groupMembers) {
    return new PrivFindPrivacyGroupSuccess(transactions.findPrivacyGroup(groupMembers), numGroups);
  }

  public Condition eeaSendRawTransactionSuccess(
      final String transaction, final String sender, final String privacyGroupId) {
    return new EeaSendRawTransactionWithCountVerification(
        transactions.sendRawTransaction(transaction), sender, privacyGroupId);
  }

  public Condition privDistributeRawTransaction(
      final String transaction, final String enclaveResponseKey) {
    return new PrivDistributeRawTransactionSuccess(
        transactions.distributeRawTransaction(transaction), enclaveResponseKey);
  }
}
