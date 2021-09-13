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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.perm;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

public class NodeSmartContractPermissioningV2Transactions {

  private final Accounts accounts;

  public NodeSmartContractPermissioningV2Transactions(final Accounts accounts) {
    this.accounts = accounts;
  }

  public Transaction<Hash> allowNode(final String contractAddress, final Node node) {
    return new NodeSmartContractPermissioningAllowNodeV2Transaction(
        accounts.getPrimaryBenefactor(), Address.fromHexString(contractAddress), node);
  }

  public Transaction<Hash> allowNode(final String contractAddress, final EnodeURL enodeURL) {
    return new NodeSmartContractPermissioningAllowNodeByURLV2Transaction(
        accounts.getPrimaryBenefactor(), Address.fromHexString(contractAddress), enodeURL);
  }

  public Transaction<Hash> forbidNode(final String contractAddress, final Node node) {
    return new NodeSmartContractPermissioningForbidNodeV2Transaction(
        accounts.getPrimaryBenefactor(), Address.fromHexString(contractAddress), node);
  }

  public Transaction<Hash> forbidNode(final String contractAddress, final EnodeURL enodeURL) {
    return new NodeSmartContractPermissioningForbidNodeByURLV2Transaction(
        accounts.getPrimaryBenefactor(), Address.fromHexString(contractAddress), enodeURL);
  }

  public Transaction<Boolean> isConnectionAllowed(final String contractAddress, final Node node) {
    return new NodeSmartContractPermissioningConnectionIsAllowedV2Transaction(
        Address.fromHexString(contractAddress), node);
  }

  public Transaction<Boolean> isConnectionAllowed(
      final String contractAddress, final EnodeURL enodeURL) {
    return new NodeSmartContractPermissioningConnectionIsAllowedByURLV2Transaction(
        Address.fromHexString(contractAddress), enodeURL);
  }
}
