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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.P2pDisabledException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Hex;

public class PermGetNodesWhitelist implements JsonRpcMethod {

  private final P2PNetwork p2pNetwork;

  public PermGetNodesWhitelist(final P2PNetwork p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
  }

  @Override
  public String getName() {
    return "perm_getNodesWhitelist";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    try {
      if (p2pNetwork.getNodeWhitelistController().isPresent()) {
        List<Peer> nodesWhitelist =
            p2pNetwork.getNodeWhitelistController().get().getNodesWhitelist();
        List<String> enodeList =
            nodesWhitelist.parallelStream().map(this::buildEnodeURI).collect(Collectors.toList());

        return new JsonRpcSuccessResponse(req.getId(), enodeList);
      } else {
        return new JsonRpcErrorResponse(req.getId(), JsonRpcError.NODE_WHITELIST_NOT_ENABLED);
      }
    } catch (P2pDisabledException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    }
  }

  private String buildEnodeURI(final Peer s) {
    String url = Hex.toHexString(s.getId().extractArray());
    Endpoint endpoint = s.getEndpoint();
    String nodeIp = endpoint.getHost();
    OptionalInt tcpPort = endpoint.getTcpPort();
    int udpPort = endpoint.getUdpPort();

    if (tcpPort.isPresent() && (tcpPort.getAsInt() != udpPort)) {
      return String.format(
          "enode://%s@%s:%d?discport=%d", url, nodeIp, tcpPort.getAsInt(), udpPort);
    } else {
      return String.format("enode://%s@%s:%d", url, nodeIp, udpPort);
    }
  }
}
