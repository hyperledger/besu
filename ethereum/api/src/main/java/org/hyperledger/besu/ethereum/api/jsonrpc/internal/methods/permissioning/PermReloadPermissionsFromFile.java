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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.Optional;

public class PermReloadPermissionsFromFile implements JsonRpcMethod {

  private final Optional<AccountLocalConfigPermissioningController> accountAllowlistController;
  private final Optional<NodeLocalConfigPermissioningController> nodesAllowlistController;

  public PermReloadPermissionsFromFile(
      final Optional<AccountLocalConfigPermissioningController> accountAllowlistController,
      final Optional<NodeLocalConfigPermissioningController> nodesAllowlistController) {
    this.accountAllowlistController = accountAllowlistController;
    this.nodesAllowlistController = nodesAllowlistController;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_RELOAD_PERMISSIONS_FROM_FILE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (!accountAllowlistController.isPresent() && !nodesAllowlistController.isPresent()) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.PERMISSIONING_NOT_ENABLED);
    }

    try {
      accountAllowlistController.ifPresent(AccountLocalConfigPermissioningController::reload);
      nodesAllowlistController.ifPresent(NodeLocalConfigPermissioningController::reload);
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
    } catch (Exception e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.ALLOWLIST_RELOAD_ERROR);
    }
  }
}
