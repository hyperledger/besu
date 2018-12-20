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
package tech.pegasys.pantheon.ethereum.p2p.permissioning;

import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class NodeWhitelistController {

  private final List<Peer> nodeWhitelist;
  private boolean nodeWhitelistSet = false;

  public NodeWhitelistController(final PermissioningConfiguration configuration) {
    nodeWhitelist = new ArrayList<>();
    if (configuration != null && configuration.getNodeWhitelist() != null) {
      for (URI uri : configuration.getNodeWhitelist()) {
        nodeWhitelist.add(DefaultPeer.fromURI(uri));
      }
      if (configuration.isNodeWhitelistSet()) {
        nodeWhitelistSet = true;
      }
    }
  }

  public boolean addNode(final Peer node) {
    nodeWhitelistSet = true;
    return nodeWhitelist.add(node);
  }

  public boolean removeNode(final Peer node) {
    return nodeWhitelist.remove(node);
  }

  public boolean contains(final Peer node) {
    return (!nodeWhitelistSet || (nodeWhitelistSet && nodeWhitelist.contains(node)));
  }
}
