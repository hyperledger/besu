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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.permissioning;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.permissioning.AccountLocalConfigPermissioningController;

import java.util.Optional;

public class PermGetAccountsWhitelist implements JsonRpcMethod {

  private final Optional<AccountLocalConfigPermissioningController> whitelistController;

  public PermGetAccountsWhitelist(
      final Optional<AccountLocalConfigPermissioningController> whitelistController) {
    this.whitelistController = whitelistController;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_GET_ACCOUNTS_WHITELIST.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (whitelistController.isPresent()) {
      return new JsonRpcSuccessResponse(
          request.getId(), whitelistController.get().getAccountWhitelist());
    } else {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.ACCOUNT_WHITELIST_NOT_ENABLED);
    }
  }
}
