/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;

import java.util.Optional;

public class NetEnode implements JsonRpcMethod {

  private final P2PNetwork p2pNetwork;

  public NetEnode(final P2PNetwork p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
  }

  @Override
  public String getName() {
    return RpcMethod.NET_ENODE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    if (!p2pNetwork.isP2pEnabled()) {
      return p2pDisabledResponse(req);
    }

    final Optional<EnodeURL> enodeURL = p2pNetwork.getLocalEnode();
    if (!enodeURL.isPresent()) {
      return enodeUrlNotAvailable(req);
    }

    return new JsonRpcSuccessResponse(req.getId(), enodeURL.get().toString());
  }

  private JsonRpcErrorResponse p2pDisabledResponse(final JsonRpcRequest req) {
    return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
  }

  private JsonRpcErrorResponse enodeUrlNotAvailable(final JsonRpcRequest req) {
    return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_NETWORK_NOT_RUNNING);
  }
}
