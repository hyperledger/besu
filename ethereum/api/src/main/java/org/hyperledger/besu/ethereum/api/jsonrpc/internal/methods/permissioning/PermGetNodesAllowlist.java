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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.List;
import java.util.Optional;

public class PermGetNodesAllowlist implements JsonRpcMethod {

  private final Optional<NodeLocalConfigPermissioningController>
      nodeAllowlistPermissioningController;

  public PermGetNodesAllowlist(
      final Optional<NodeLocalConfigPermissioningController> nodeAllowlistPermissioningController) {
    this.nodeAllowlistPermissioningController = nodeAllowlistPermissioningController;
  }

  @Override
  public String getName() {
    return RpcMethod.PERM_GET_NODES_ALLOWLIST.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      if (nodeAllowlistPermissioningController.isPresent()) {
        final List<String> enodeList =
            nodeAllowlistPermissioningController.get().getNodesAllowlist();

        return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), enodeList);
      } else {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_NOT_ENABLED);
      }
    } catch (P2PDisabledException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.P2P_DISABLED);
    }
  }
}
