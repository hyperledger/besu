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

import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.NodeSmartContractPermissioningV2Transactions;

public class NodeSmartContractPermissioningV2Conditions {

  private final NodeSmartContractPermissioningV2Transactions transactions;

  public NodeSmartContractPermissioningV2Conditions(
      final NodeSmartContractPermissioningV2Transactions transactions) {
    this.transactions = transactions;
  }

  public Condition connectionIsAllowed(final String address, final Node node) {
    return new WaitForTrueResponse(transactions.isConnectionAllowed(address, node));
  }

  public Condition connectionIsAllowed(final String address, final EnodeURL enodeURL) {
    return new WaitForTrueResponse(transactions.isConnectionAllowed(address, enodeURL));
  }

  public Condition connectionIsForbidden(final String address, final Node node) {
    return new WaitForFalseResponse(transactions.isConnectionAllowed(address, node));
  }

  public Condition connectionIsForbidden(final String address, final EnodeURL enodeURL) {
    return new WaitForFalseResponse(transactions.isConnectionAllowed(address, enodeURL));
  }
}
