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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddAccountsToAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddAccountsToWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddNodesToAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddNodesToWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetAccountsAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetAccountsWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetNodesAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetNodesWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermReloadPermissionsFromFile;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromWhitelist;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.Map;
import java.util.Optional;

public class PermJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final Optional<AccountLocalConfigPermissioningController> accountsAllowlistController;
  private final Optional<NodeLocalConfigPermissioningController> nodeAllowlistController;

  public PermJsonRpcMethods(
      final Optional<AccountLocalConfigPermissioningController> accountsAllowlistController,
      final Optional<NodeLocalConfigPermissioningController> nodeAllowlistController) {
    this.accountsAllowlistController = accountsAllowlistController;
    this.nodeAllowlistController = nodeAllowlistController;
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.PERM.name();
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    return mapOf(
        new PermAddNodesToWhitelist(nodeAllowlistController),
        new PermAddNodesToAllowlist(nodeAllowlistController),
        new PermRemoveNodesFromWhitelist(nodeAllowlistController),
        new PermRemoveNodesFromAllowlist(nodeAllowlistController),
        new PermGetNodesWhitelist(nodeAllowlistController),
        new PermGetNodesAllowlist(nodeAllowlistController),
        new PermGetAccountsWhitelist(accountsAllowlistController),
        new PermGetAccountsAllowlist(accountsAllowlistController),
        new PermAddAccountsToWhitelist(accountsAllowlistController),
        new PermAddAccountsToAllowlist(accountsAllowlistController),
        new PermRemoveAccountsFromWhitelist(accountsAllowlistController),
        new PermRemoveAccountsFromAllowlist(accountsAllowlistController),
        new PermReloadPermissionsFromFile(accountsAllowlistController, nodeAllowlistController));
  }
}
