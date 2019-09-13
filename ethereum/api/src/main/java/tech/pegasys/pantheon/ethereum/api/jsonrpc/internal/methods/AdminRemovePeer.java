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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminRemovePeer extends AdminModifyPeer {

  private static final Logger LOG = LogManager.getLogger();

  public AdminRemovePeer(final P2PNetwork peerNetwork, final JsonRpcParameter parameters) {
    super(peerNetwork, parameters);
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_REMOVE_PEER.getMethodName();
  }

  @Override
  protected JsonRpcResponse performOperation(final Object id, final String enode) {
    LOG.debug("Remove ({}) from peer cache", enode);
    final EnodeURL enodeURL = EnodeURL.fromString(enode);
    final boolean result =
        peerNetwork.removeMaintainedConnectionPeer(DefaultPeer.fromEnodeURL(enodeURL));
    return new JsonRpcSuccessResponse(id, result);
  }
}
