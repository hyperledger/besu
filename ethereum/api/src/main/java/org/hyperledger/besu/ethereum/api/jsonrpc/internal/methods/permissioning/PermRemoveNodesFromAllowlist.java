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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.StringListParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController.NodesAllowlistResult;

import java.util.List;
import java.util.Optional;

public class PermRemoveNodesFromAllowlist implements JsonRpcMethod {

  private final Optional<NodeLocalConfigPermissioningController>
      nodeAllowlistPermissioningController;

  public PermRemoveNodesFromAllowlist(
      final Optional<NodeLocalConfigPermissioningController> nodeAllowlistPermissioningController) {
    this.nodeAllowlistPermissioningController = nodeAllowlistPermissioningController;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_REMOVE_NODES_FROM_ALLOWLIST.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final StringListParameter enodeListParam =
        requestContext.getRequiredParameter(0, StringListParameter.class);
    try {
      if (nodeAllowlistPermissioningController.isPresent()) {
        try {
          final List<String> enodeURLs = enodeListParam.getStringList();
          final NodesAllowlistResult nodesAllowlistResult =
              nodeAllowlistPermissioningController.get().removeNodes(enodeURLs);

          switch (nodesAllowlistResult.result()) {
            case SUCCESS:
              return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
            case ERROR_EMPTY_ENTRY:
              return new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(), JsonRpcError.NODE_ALLOWLIST_EMPTY_ENTRY);
            case ERROR_ABSENT_ENTRY:
              return new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(), JsonRpcError.NODE_ALLOWLIST_MISSING_ENTRY);
            case ERROR_DUPLICATED_ENTRY:
              return new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(),
                  JsonRpcError.NODE_ALLOWLIST_DUPLICATED_ENTRY);
            case ERROR_ALLOWLIST_PERSIST_FAIL:
              return new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(), JsonRpcError.ALLOWLIST_PERSIST_FAILURE);
            case ERROR_ALLOWLIST_FILE_SYNC:
              return new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(), JsonRpcError.ALLOWLIST_FILE_SYNC);
            case ERROR_FIXED_NODE_CANNOT_BE_REMOVED:
              return new JsonRpcErrorResponse(
                  requestContext.getRequest().getId(),
                  JsonRpcError.NODE_ALLOWLIST_FIXED_NODE_CANNOT_BE_REMOVED);
            default:
              throw new Exception();
          }
        } catch (IllegalArgumentException e) {
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.NODE_ALLOWLIST_INVALID_ENTRY);
        } catch (Exception e) {
          return new JsonRpcErrorResponse(
              requestContext.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);
        }
      } else {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.NODE_ALLOWLIST_NOT_ENABLED);
      }
    } catch (P2PDisabledException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.P2P_DISABLED);
    }
  }
}
