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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.WhitelistOperationResult;

import java.util.List;
import java.util.Optional;

public class PermRemoveAccountsFromWhitelist implements JsonRpcMethod {

  private final JsonRpcParameter parameters;
  private final Optional<AccountLocalConfigPermissioningController> whitelistController;

  public PermRemoveAccountsFromWhitelist(
      final Optional<AccountLocalConfigPermissioningController> whitelistController,
      final JsonRpcParameter parameters) {
    this.whitelistController = whitelistController;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_REMOVE_ACCOUNTS_FROM_WHITELIST.getMethodName();
  }

  @Override
  @SuppressWarnings("unchecked")
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final List<String> accountsList = parameters.required(request.getParams(), 0, List.class);
    if (whitelistController.isPresent()) {
      final WhitelistOperationResult removeResult =
          whitelistController.get().removeAccounts(accountsList);

      switch (removeResult) {
        case ERROR_EMPTY_ENTRY:
          return new JsonRpcErrorResponse(
              request.getId(), JsonRpcError.ACCOUNT_WHITELIST_EMPTY_ENTRY);
        case ERROR_INVALID_ENTRY:
          return new JsonRpcErrorResponse(
              request.getId(), JsonRpcError.ACCOUNT_WHITELIST_INVALID_ENTRY);
        case ERROR_ABSENT_ENTRY:
          return new JsonRpcErrorResponse(
              request.getId(), JsonRpcError.ACCOUNT_WHITELIST_ABSENT_ENTRY);
        case ERROR_DUPLICATED_ENTRY:
          return new JsonRpcErrorResponse(
              request.getId(), JsonRpcError.ACCOUNT_WHITELIST_DUPLICATED_ENTRY);
        case ERROR_WHITELIST_PERSIST_FAIL:
          return new JsonRpcErrorResponse(request.getId(), JsonRpcError.WHITELIST_PERSIST_FAILURE);
        case ERROR_WHITELIST_FILE_SYNC:
          return new JsonRpcErrorResponse(request.getId(), JsonRpcError.WHITELIST_FILE_SYNC);
        case SUCCESS:
          return new JsonRpcSuccessResponse(request.getId());
        default:
          throw new IllegalStateException(
              "Unmapped result from AccountLocalConfigPermissioningController");
      }
    } else {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.ACCOUNT_WHITELIST_NOT_ENABLED);
    }
  }
}
