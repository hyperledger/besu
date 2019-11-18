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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.StringListParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.List;
import java.util.Optional;

public class PermRemoveNodesFromWhitelist implements JsonRpcMethod {

  private final Optional<NodeLocalConfigPermissioningController>
      nodeWhitelistPermissioningController;

  public PermRemoveNodesFromWhitelist(
      final Optional<NodeLocalConfigPermissioningController> nodeWhitelistPermissioningController) {
    this.nodeWhitelistPermissioningController = nodeWhitelistPermissioningController;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_REMOVE_NODES_FROM_WHITELIST.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    final StringListParameter enodeListParam =
        req.getRequiredParameter(0, StringListParameter.class);
    try {
      if (nodeWhitelistPermissioningController.isPresent()) {
        try {
          final List<String> enodeURLs = enodeListParam.getStringList();
          final NodeLocalConfigPermissioningController.NodesWhitelistResult nodesWhitelistResult =
              nodeWhitelistPermissioningController.get().removeNodes(enodeURLs);

          switch (nodesWhitelistResult.result()) {
            case SUCCESS:
              return new JsonRpcSuccessResponse(req.getId());
            case ERROR_EMPTY_ENTRY:
              return new JsonRpcErrorResponse(req.getId(), JsonRpcError.NODE_WHITELIST_EMPTY_ENTRY);
            case ERROR_ABSENT_ENTRY:
              return new JsonRpcErrorResponse(
                  req.getId(), JsonRpcError.NODE_WHITELIST_MISSING_ENTRY);
            case ERROR_DUPLICATED_ENTRY:
              return new JsonRpcErrorResponse(
                  req.getId(), JsonRpcError.NODE_WHITELIST_DUPLICATED_ENTRY);
            case ERROR_WHITELIST_PERSIST_FAIL:
              return new JsonRpcErrorResponse(req.getId(), JsonRpcError.WHITELIST_PERSIST_FAILURE);
            case ERROR_WHITELIST_FILE_SYNC:
              return new JsonRpcErrorResponse(req.getId(), JsonRpcError.WHITELIST_FILE_SYNC);
            case ERROR_FIXED_NODE_CANNOT_BE_REMOVED:
              return new JsonRpcErrorResponse(
                  req.getId(), JsonRpcError.NODE_WHITELIST_FIXED_NODE_CANNOT_BE_REMOVED);
            default:
              throw new Exception();
          }
        } catch (IllegalArgumentException e) {
          return new JsonRpcErrorResponse(req.getId(), JsonRpcError.NODE_WHITELIST_INVALID_ENTRY);
        } catch (Exception e) {
          return new JsonRpcErrorResponse(req.getId(), JsonRpcError.INTERNAL_ERROR);
        }
      } else {
        return new JsonRpcErrorResponse(req.getId(), JsonRpcError.NODE_WHITELIST_NOT_ENABLED);
      }
    } catch (P2PDisabledException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    }
  }
}
