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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.PeerResult;
import tech.pegasys.pantheon.ethereum.p2p.P2pDisabledException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;

import java.util.List;
import java.util.stream.Collectors;

public class AdminPeers implements JsonRpcMethod {
  private final P2PNetwork peerDiscoveryAgent;

  public AdminPeers(final P2PNetwork peerDiscoveryAgent) {
    this.peerDiscoveryAgent = peerDiscoveryAgent;
  }

  @Override
  public String getName() {
    return "admin_peers";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    try {
      final List<PeerResult> peers =
          peerDiscoveryAgent.getPeers().stream().map(PeerResult::new).collect(Collectors.toList());
      final JsonRpcResponse result = new JsonRpcSuccessResponse(req.getId(), peers);
      return result;
    } catch (P2pDisabledException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    }
  }
}
