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

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.ConnectingToLocalNodeException;
import tech.pegasys.pantheon.ethereum.p2p.PeerNotPermittedException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminAddPeer extends AdminModifyPeer {

  private static final Logger LOG = LogManager.getLogger();

  public AdminAddPeer(final P2PNetwork peerNetwork, final JsonRpcParameter parameters) {
    super(peerNetwork, parameters);
  }

  @Override
  public String getName() {
    return "admin_addPeer";
  }

  @Override
  protected JsonRpcResponse performOperation(final Object id, final String enode) {
    try {
      LOG.debug("Adding ({}) to peers", enode);
      final EnodeURL enodeURL = EnodeURL.fromString(enode);
      final Peer peer = DefaultPeer.fromEnodeURL(enodeURL);
      boolean addedToNetwork = peerNetwork.addMaintainConnectionPeer(peer);
      return new JsonRpcSuccessResponse(id, addedToNetwork);
    } catch (final PeerNotPermittedException e) {
      return new JsonRpcErrorResponse(
          id, JsonRpcError.NON_PERMITTED_NODE_CANNOT_BE_ADDED_AS_A_PEER);
    } catch (final ConnectingToLocalNodeException e) {
      return new JsonRpcErrorResponse(id, JsonRpcError.CANT_CONNECT_TO_LOCAL_PEER);
    }
  }
}
