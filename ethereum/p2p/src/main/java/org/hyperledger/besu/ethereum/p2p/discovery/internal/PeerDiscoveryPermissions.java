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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PermissionsUpdateCallback;

class PeerDiscoveryPermissions {
  private final Peer localNode;
  private final PeerPermissions peerPermissions;

  PeerDiscoveryPermissions(final Peer localNode, final PeerPermissions peerPermissions) {
    this.localNode = localNode;
    this.peerPermissions = peerPermissions;
  }

  public void subscribeUpdate(final PermissionsUpdateCallback callback) {
    peerPermissions.subscribeUpdate(callback);
  }

  public boolean isAllowedInPeerTable(final Peer peer) {
    return peerPermissions.isPermitted(
        localNode, peer, PeerPermissions.Action.DISCOVERY_ALLOW_IN_PEER_TABLE);
  }

  public boolean allowOutboundBonding(final Peer peer) {
    return peerPermissions.isPermitted(
        localNode, peer, PeerPermissions.Action.DISCOVERY_ALLOW_OUTBOUND_BONDING);
  }

  public boolean allowInboundBonding(final Peer peer) {
    return peerPermissions.isPermitted(
        localNode, peer, PeerPermissions.Action.DISCOVERY_ACCEPT_INBOUND_BONDING);
  }

  public boolean allowOutboundNeighborsRequest(final Peer peer) {
    return peerPermissions.isPermitted(
        localNode, peer, PeerPermissions.Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST);
  }

  public boolean allowInboundNeighborsRequest(final Peer peer) {
    return peerPermissions.isPermitted(
        localNode, peer, PeerPermissions.Action.DISCOVERY_SERVE_INBOUND_NEIGHBORS_REQUEST);
  }
}
