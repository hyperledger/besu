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
package org.hyperledger.besu.ethereum.p2p.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsBlacklist;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.util.bytes.BytesValue;

import org.junit.Test;

public class PeerReputationManagerTest {
  private final Peer localNode = generatePeer();
  private final PeerReputationManager peerReputationManager;
  private final PeerPermissionsBlacklist blacklist;

  public PeerReputationManagerTest() {
    blacklist = PeerPermissionsBlacklist.create();
    peerReputationManager = new PeerReputationManager(blacklist);
  }

  @Test
  public void doesNotBlacklistPeerForNormalDisconnect() {
    final PeerConnection peer = generatePeerConnection();

    checkPermissions(blacklist, peer.getPeer(), true);

    peerReputationManager.onDisconnect(peer, DisconnectReason.TOO_MANY_PEERS, false);

    checkPermissions(blacklist, peer.getPeer(), true);
  }

  @Test
  public void blacklistPeerForBadBehavior() {
    final PeerConnection peer = generatePeerConnection();

    checkPermissions(blacklist, peer.getPeer(), true);
    peerReputationManager.onDisconnect(peer, DisconnectReason.BREACH_OF_PROTOCOL, false);
    checkPermissions(blacklist, peer.getPeer(), false);
  }

  @Test
  public void doesNotBlacklistPeerForOurBadBehavior() {
    final PeerConnection peer = generatePeerConnection();

    checkPermissions(blacklist, peer.getPeer(), true);
    peerReputationManager.onDisconnect(peer, DisconnectReason.BREACH_OF_PROTOCOL, true);
    checkPermissions(blacklist, peer.getPeer(), true);
  }

  @Test
  public void blacklistIncompatiblePeer() {
    final PeerConnection peer = generatePeerConnection();

    checkPermissions(blacklist, peer.getPeer(), true);
    peerReputationManager.onDisconnect(
        peer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, false);
    checkPermissions(blacklist, peer.getPeer(), false);
  }

  @Test
  public void blacklistIncompatiblePeerWhoIssuesDisconnect() {
    final PeerConnection peer = generatePeerConnection();

    checkPermissions(blacklist, peer.getPeer(), true);
    peerReputationManager.onDisconnect(
        peer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, true);
    checkPermissions(blacklist, peer.getPeer(), false);
  }

  private void checkPermissions(
      final PeerPermissionsBlacklist blacklist,
      final Peer remotePeer,
      final boolean expectedResult) {
    for (PeerPermissions.Action action : PeerPermissions.Action.values()) {
      assertThat(blacklist.isPermitted(localNode, remotePeer, action)).isEqualTo(expectedResult);
    }
  }

  private PeerConnection generatePeerConnection() {
    final BytesValue nodeId = Peer.randomId();
    final PeerConnection conn = mock(PeerConnection.class);
    final PeerInfo peerInfo = mock(PeerInfo.class);
    final Peer peer = generatePeer();

    when(peerInfo.getNodeId()).thenReturn(nodeId);
    when(conn.getPeerInfo()).thenReturn(peerInfo);
    when(conn.getPeer()).thenReturn(peer);

    return conn;
  }

  private Peer generatePeer() {
    return DefaultPeer.fromEnodeURL(
        EnodeURL.builder()
            .nodeId(Peer.randomId())
            .ipAddress("10.9.8.7")
            .discoveryPort(65535)
            .listeningPort(65534)
            .build());
  }
}
