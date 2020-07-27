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
import org.hyperledger.besu.ethereum.permissioning.AllowlistOperationResult;

import java.util.List;
import java.util.Optional;

public class PermRemoveAccountsFromAllowlist implements JsonRpcMethod {

  private final Optional<AccountLocalConfigPermissioningController> allowlistController;

  public PermRemoveAccountsFromAllowlist(
      final Optional<AccountLocalConfigPermissioningController> allowlistController) {
    this.allowlistController = allowlistController;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_REMOVE_ACCOUNTS_FROM_ALLOWLIST.getMethodName();
  }

  @Override
  @SuppressWarnings("unchecked")
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final List<String> accountsList = requestContext.getRequiredParameter(0, List.class);
    if (allowlistController.isPresent()) {
      final AllowlistOperationResult removeResult =
          allowlistController.get().removeAccounts(accountsList);

      switch (removeResult) {
        case ERROR_EMPTY_ENTRY:
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.ACCOUNT_ALLOWLIST_EMPTY_ENTRY);
        case ERROR_INVALID_ENTRY:
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.ACCOUNT_ALLOWLIST_INVALID_ENTRY);
        case ERROR_ABSENT_ENTRY:
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.ACCOUNT_ALLOWLIST_ABSENT_ENTRY);
        case ERROR_DUPLICATED_ENTRY:
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.ACCOUNT_ALLOWLIST_DUPLICATED_ENTRY);
        case ERROR_ALLOWLIST_PERSIST_FAIL:
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.ALLOWLIST_PERSIST_FAILURE);
        case ERROR_ALLOWLIST_FILE_SYNC:
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.ALLOWLIST_FILE_SYNC);
        case SUCCESS:
          return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
        default:
          throw new IllegalStateException(
              "Unmapped result from AccountLocalConfigPermissioningController");
      }
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.ACCOUNT_ALLOWLIST_NOT_ENABLED);
    }
  }
}
