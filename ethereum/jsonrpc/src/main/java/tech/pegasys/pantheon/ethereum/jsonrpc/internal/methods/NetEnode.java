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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.P2pDisabledException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetEnode implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final P2PNetwork p2pNetwork;

  public NetEnode(final P2PNetwork p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
  }

  @Override
  public String getName() {
    return "net_enode";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    try {
      if (p2pNetwork.isP2pEnabled()) {
        String enodeURI = p2pNetwork.getAdvertisedPeer().map(Peer::getEnodeURLString).orElse("");
        if (!enodeURI.isEmpty()) {
          return new JsonRpcSuccessResponse(req.getId(), enodeURI);
        } else {
          return p2pDisabledResponse(req);
        }
      } else {
        return p2pDisabledResponse(req);
      }
    } catch (final P2pDisabledException e) {
      return p2pDisabledResponse(req);
    } catch (final Exception e) {
      LOG.error("Error processing request: " + req, e);
      throw e;
    }
  }

  private JsonRpcErrorResponse p2pDisabledResponse(final JsonRpcRequest req) {
    return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
  }
}
