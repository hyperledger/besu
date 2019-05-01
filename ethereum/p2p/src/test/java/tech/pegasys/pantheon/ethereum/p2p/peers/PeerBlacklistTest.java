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
package tech.pegasys.pantheon.ethereum.p2p.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.Collections;

import org.junit.Test;

public class PeerBlacklistTest {
  private int nodeIdValue = 1;

  @Test
  public void directlyAddingPeerWorks() {
    final PeerBlacklist blacklist = new PeerBlacklist();
    final Peer peer = generatePeer();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.add(peer);

    assertThat(blacklist.contains(peer)).isTrue();
  }

  @Test
  public void directlyAddingPeerByPeerIdWorks() {
    final PeerBlacklist blacklist = new PeerBlacklist();
    final Peer peer = generatePeer();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.add(peer.getId());

    assertThat(blacklist.contains(peer)).isTrue();
  }

  @Test
  public void banningPeerByPeerIdWorks() {
    final Peer peer = generatePeer();
    final PeerBlacklist blacklist = new PeerBlacklist(Collections.singleton(peer.getId()));

    assertThat(blacklist.contains(peer)).isTrue();

    blacklist.add(peer.getId());

    assertThat(blacklist.contains(peer)).isTrue();
  }

  @Test
  public void bannedNodesDoNotRollover() {
    final Peer bannedPeer = generatePeer();
    final Peer peer1 = generatePeer();
    final Peer peer2 = generatePeer();
    final Peer peer3 = generatePeer();
    final PeerBlacklist blacklist = new PeerBlacklist(2, Collections.singleton(bannedPeer.getId()));

    assertThat(blacklist.contains(bannedPeer)).isTrue();
    assertThat(blacklist.contains(peer1)).isFalse();
    assertThat(blacklist.contains(peer2)).isFalse();
    assertThat(blacklist.contains(peer3)).isFalse();

    // fill to the limit
    blacklist.add(peer1.getId());
    blacklist.add(peer2.getId());
    assertThat(blacklist.contains(bannedPeer)).isTrue();
    assertThat(blacklist.contains(peer1)).isTrue();
    assertThat(blacklist.contains(peer2)).isTrue();
    assertThat(blacklist.contains(peer3)).isFalse();

    // trigger rollover
    blacklist.add(peer3.getId());
    assertThat(blacklist.contains(bannedPeer)).isTrue();
    assertThat(blacklist.contains(peer1)).isFalse();
    assertThat(blacklist.contains(peer2)).isTrue();
    assertThat(blacklist.contains(peer3)).isTrue();
  }

  @Test
  public void doesNotBlacklistPeerForNormalDisconnect() {
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerConnection peer = generatePeerConnection();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.onDisconnect(peer, DisconnectReason.TOO_MANY_PEERS, false);

    assertThat(blacklist.contains(peer)).isFalse();
  }

  @Test
  public void blacklistPeerForBadBehavior() {

    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerConnection peer = generatePeerConnection();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.onDisconnect(peer, DisconnectReason.BREACH_OF_PROTOCOL, false);

    assertThat(blacklist.contains(peer)).isTrue();
  }

  @Test
  public void doesNotBlacklistPeerForOurBadBehavior() {
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerConnection peer = generatePeerConnection();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.onDisconnect(peer, DisconnectReason.BREACH_OF_PROTOCOL, true);

    assertThat(blacklist.contains(peer)).isFalse();
  }

  @Test
  public void blacklistIncompatiblePeer() {
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerConnection peer = generatePeerConnection();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.onDisconnect(peer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, false);

    assertThat(blacklist.contains(peer)).isTrue();
  }

  @Test
  public void blacklistIncompatiblePeerWhoIssuesDisconnect() {
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerConnection peer = generatePeerConnection();

    assertThat(blacklist.contains(peer)).isFalse();

    blacklist.onDisconnect(peer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, true);

    assertThat(blacklist.contains(peer)).isTrue();
  }

  @Test
  public void capsSizeOfList() {

    final PeerBlacklist blacklist = new PeerBlacklist(2);
    final PeerConnection peer1 = generatePeerConnection();
    final PeerConnection peer2 = generatePeerConnection();
    final PeerConnection peer3 = generatePeerConnection();

    // Add first peer
    blacklist.onDisconnect(peer1, DisconnectReason.BREACH_OF_PROTOCOL, false);
    assertThat(blacklist.contains(peer1)).isTrue();
    assertThat(blacklist.contains(peer2)).isFalse();
    assertThat(blacklist.contains(peer3)).isFalse();

    // Add second peer
    blacklist.onDisconnect(peer2, DisconnectReason.BREACH_OF_PROTOCOL, false);
    assertThat(blacklist.contains(peer1)).isTrue();
    assertThat(blacklist.contains(peer2)).isTrue();
    assertThat(blacklist.contains(peer3)).isFalse();

    // Adding third peer should kick out least recently accessed peer
    blacklist.onDisconnect(peer3, DisconnectReason.BREACH_OF_PROTOCOL, false);
    assertThat(blacklist.contains(peer1)).isFalse();
    assertThat(blacklist.contains(peer2)).isTrue();
    assertThat(blacklist.contains(peer3)).isTrue();

    // Adding peer1 back in should kick out peer2
    blacklist.onDisconnect(peer1, DisconnectReason.BREACH_OF_PROTOCOL, false);
    assertThat(blacklist.contains(peer1)).isTrue();
    assertThat(blacklist.contains(peer2)).isFalse();
    assertThat(blacklist.contains(peer3)).isTrue();

    // Adding peer2 back in should kick out peer3
    blacklist.onDisconnect(peer2, DisconnectReason.BREACH_OF_PROTOCOL, false);
    assertThat(blacklist.contains(peer1)).isTrue();
    assertThat(blacklist.contains(peer2)).isTrue();
    assertThat(blacklist.contains(peer3)).isFalse();
  }

  private PeerConnection generatePeerConnection() {
    final BytesValue nodeId = BytesValue.of(nodeIdValue++);
    final PeerConnection peer = mock(PeerConnection.class);
    final PeerInfo peerInfo = mock(PeerInfo.class);

    when(peerInfo.getNodeId()).thenReturn(nodeId);
    when(peer.getPeerInfo()).thenReturn(peerInfo);

    return peer;
  }

  private Peer generatePeer() {
    final byte[] id = new byte[64];
    id[0] = (byte) nodeIdValue++;
    return DefaultPeer.fromEnodeURL(
        EnodeURL.builder()
            .nodeId(id)
            .ipAddress("10.9.8.7")
            .discoveryPort(65535)
            .listeningPort(65534)
            .build());
  }
}
