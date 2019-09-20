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
package org.hyperledger.besu.tests.acceptance.dsl.condition.perm;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.NodeSmartContractPermissioningTransactions;

public class NodeSmartContractPermissioningConditions {

  private final NodeSmartContractPermissioningTransactions transactions;

  public NodeSmartContractPermissioningConditions(
      final NodeSmartContractPermissioningTransactions transactions) {
    this.transactions = transactions;
  }

  public Condition nodeIsAllowed(final String address, final Node node) {
    return new WaitForTrueResponse(transactions.isNodeAllowed(address, node));
  }

  public Condition nodeIsForbidden(final String address, final Node node) {
    return new WaitForFalseResponse(transactions.isNodeAllowed(address, node));
  }

  public Condition connectionIsAllowed(final String address, final Node source, final Node target) {
    return new WaitForTrueResponse(transactions.isConnectionAllowed(address, source, target));
  }

  public Condition connectionIsForbidden(
      final String address, final Node source, final Node target) {
    return new WaitForFalseResponse(transactions.isConnectionAllowed(address, source, target));
  }
}
