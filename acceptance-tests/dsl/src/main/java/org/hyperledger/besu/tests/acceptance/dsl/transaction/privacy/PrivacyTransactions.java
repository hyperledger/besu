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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import org.hyperledger.besu.datatypes.Hash;

import java.util.List;

public class PrivacyTransactions {
  public PrivGetPrivacyPrecompileAddressTransaction getPrivacyPrecompileAddress() {
    return new PrivGetPrivacyPrecompileAddressTransaction();
  }

  public PrivGetPrivateTransactionTransaction getPrivateTransaction(final Hash transactionHash) {
    return new PrivGetPrivateTransactionTransaction(transactionHash);
  }

  public PrivCreatePrivacyGroupTransaction createPrivacyGroup(
      final List<String> addresses, final String groupName, final String groupDescription) {
    return new PrivCreatePrivacyGroupTransaction(addresses, groupName, groupDescription);
  }

  public PrivDeletePrivacyGroupTransaction deletePrivacyGroup(final String transactionHash) {
    return new PrivDeletePrivacyGroupTransaction(transactionHash);
  }

  public PrivFindPrivacyGroupTransaction findPrivacyGroup(final String[] groupMembers) {
    return new PrivFindPrivacyGroupTransaction(groupMembers);
  }

  public EeaSendRawTransactionTransaction sendRawTransaction(final String transaction) {
    return new EeaSendRawTransactionTransaction(transaction);
  }

  public PrivDistributeRawTransactionTransaction distributeRawTransaction(
      final String transaction) {
    return new PrivDistributeRawTransactionTransaction(transaction);
  }

  public PrivGetTransactionCountTransaction getTransactionCount(
      final String accountAddress, final String privacyGroupId) {
    return new PrivGetTransactionCountTransaction(accountAddress, privacyGroupId);
  }

  public PrivGetEeaTransactionCountTransaction getEeaTransactionCount(
      final String accountAddress, final String privateFrom, final String[] privateFor) {
    return new PrivGetEeaTransactionCountTransaction(accountAddress, privateFrom, privateFor);
  }

  public PrivGetTransactionReceiptTransaction getTransactionReceipt(final Hash transactionHash) {
    return new PrivGetTransactionReceiptTransaction(transactionHash);
  }
}
