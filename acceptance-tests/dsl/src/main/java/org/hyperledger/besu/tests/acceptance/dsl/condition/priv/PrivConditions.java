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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivTransactions;

public class PrivConditions {

  private final PrivTransactions transactions;

  public PrivConditions(final PrivTransactions transactions) {
    this.transactions = transactions;
  }

  public Condition privGetPrivacyPrecompileAddressSuccess() {
    return new PrivGetPrivacyPrecompileAddressSuccess(
        transactions.privGetPrivacyPrecompileAddress());
  }

  public Condition privGetPrivateTransaction(final String transactionHash) {
    return new PrivGetPrivateTransactionSuccess(
        transactions.privGetPrivateTransaction(transactionHash));
  }

  public Condition privCreatePrivacyGroup(
      final CreatePrivacyGroupParameter params, final String groupId) {
    return new PrivCreatePrivacyGroupSuccess(transactions.privCreatePrivacyGroup(params), groupId);
  }

  public Condition privDeletePrivacyGroup(final String groupId) {
    return new PrivDeletePrivacyGroupSuccess(transactions.privDeletePrivacyGroup(groupId), groupId);
  }

  public Condition privFindPrivacyGroup(final String[] groupMembers) {
    return new PrivFindPrivacyGroupSuccess(transactions.privFindPrivacyGroup(groupMembers));
  }

  public Condition privGetTransactionCount(final Object[] params) {
    return new PrivGetTransactionCountSuccess(transactions.privGetTransactionCount(params));
  }
}
