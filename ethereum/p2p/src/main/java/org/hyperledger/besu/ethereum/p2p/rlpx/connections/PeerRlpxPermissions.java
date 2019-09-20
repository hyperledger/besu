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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsException;
import org.hyperledger.besu.ethereum.p2p.permissions.PermissionsUpdateCallback;

public class PeerRlpxPermissions {
  private final LocalNode localNode;
  private final PeerPermissions peerPermissions;

  public PeerRlpxPermissions(final LocalNode localNode, final PeerPermissions peerPermissions) {
    this.localNode = localNode;
    this.peerPermissions = peerPermissions;
  }

  public boolean allowNewOutboundConnectionTo(final Peer peer) {
    if (!localNode.isReady()) {
      return false;
    }
    return peerPermissions.isPermitted(
        localNode.getPeer(), peer, PeerPermissions.Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION);
  }

  public PeerPermissionsException newOutboundConnectionException(final Peer peer) {
    return new PeerPermissionsException(
        peer, PeerPermissions.Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION);
  }

  public boolean allowNewInboundConnectionFrom(final Peer peer) {
    if (!localNode.isReady()) {
      return false;
    }
    return peerPermissions.isPermitted(
        localNode.getPeer(), peer, PeerPermissions.Action.RLPX_ALLOW_NEW_INBOUND_CONNECTION);
  }

  public boolean allowOngoingConnection(final Peer peer, final boolean remotelyInitiated) {
    if (!localNode.isReady()) {
      return false;
    }
    final PeerPermissions.Action action =
        remotelyInitiated
            ? PeerPermissions.Action.RLPX_ALLOW_ONGOING_REMOTELY_INITIATED_CONNECTION
            : PeerPermissions.Action.RLPX_ALLOW_ONGOING_LOCALLY_INITIATED_CONNECTION;
    return peerPermissions.isPermitted(localNode.getPeer(), peer, action);
  }

  public void subscribeUpdate(final PermissionsUpdateCallback callback) {
    peerPermissions.subscribeUpdate(callback);
  }
}
