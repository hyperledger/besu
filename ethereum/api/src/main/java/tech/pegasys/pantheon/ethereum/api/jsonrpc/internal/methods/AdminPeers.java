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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.PeerResult;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.network.exceptions.P2PDisabledException;

import java.util.List;
import java.util.stream.Collectors;

public class AdminPeers implements JsonRpcMethod {
  private final P2PNetwork peerDiscoveryAgent;

  public AdminPeers(final P2PNetwork peerDiscoveryAgent) {
    this.peerDiscoveryAgent = peerDiscoveryAgent;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_PEERS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    try {
      final List<PeerResult> peers =
          peerDiscoveryAgent.getPeers().stream().map(PeerResult::new).collect(Collectors.toList());
      final JsonRpcResponse result = new JsonRpcSuccessResponse(req.getId(), peers);
      return result;
    } catch (P2PDisabledException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    }
  }
}
