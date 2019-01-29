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
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.P2pDisabledException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminAddPeer implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();
  private final P2PNetwork peerNetwork;
  private final JsonRpcParameter parameters;

  public AdminAddPeer(final P2PNetwork peerNetwork, final JsonRpcParameter parameters) {
    this.peerNetwork = peerNetwork;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "admin_addPeer";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    if (req.getParamLength() != 1) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.INVALID_PARAMS);
    }
    try {
      final String enodeString = parameters.required(req.getParams(), 0, String.class);
      final Peer peer = DefaultPeer.fromURI(enodeString);
      final boolean added = peerNetwork.addMaintainConnectionPeer(peer);
      return new JsonRpcSuccessResponse(req.getId(), added);
    } catch (final InvalidJsonRpcParameters e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.INVALID_PARAMS);
    } catch (final IllegalArgumentException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.PARSE_ERROR);
    } catch (final P2pDisabledException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.P2P_DISABLED);
    } catch (final Exception e) {
      LOG.error("Error processing request: " + req, e);
      throw e;
    }
  }
}
