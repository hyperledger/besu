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
package tech.pegasys.pantheon.ethereum.p2p.discovery;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper.AgentBuilder;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent.IncomingPacket;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.NeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;

public class PeerDiscoveryAgentTest {

  private static final int BROADCAST_TCP_PORT = 30303;
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void neighborsPacketFromUnbondedPeerIsDropped() {
    // Start an agent with no bootstrap peers.
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getPeers()).isEmpty();

    // Start a test peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();

    // Generate an out-of-band NEIGHBORS message.
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);
    final NeighborsPacketData data = NeighborsPacketData.create(peers);
    final Packet packet = Packet.create(PacketType.NEIGHBORS, data, otherNode.getKeyPair());
    helper.sendMessageBetweenAgents(otherNode, agent, packet);

    assertThat(agent.getPeers()).isEmpty();
  }

  @Test
  public void neighborsPacketLimited() {
    // Start 20 agents with no bootstrap peers.
    final List<MockPeerDiscoveryAgent> otherAgents =
        helper.startDiscoveryAgents(20, Collections.emptyList());
    final List<DiscoveryPeer> otherPeers =
        otherAgents.stream()
            .map(MockPeerDiscoveryAgent::getAdvertisedPeer)
            .map(Optional::get)
            .collect(Collectors.toList());

    // Start another peer pointing to those 20 agents.
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(otherPeers);
    // We used to do a hasSize match but we had issues with duplicate peers getting added to the
    // list.  By moving to a contains we make sure that all the peers are loaded with tolerance for
    // duplicates.  If we fix the duplication problem we should use containsExactlyInAnyOrder to
    // hedge against missing one and duplicating another.
    assertThat(agent.getPeers()).contains(otherPeers.toArray(new DiscoveryPeer[20]));
    assertThat(agent.getPeers()).allMatch(p -> p.getStatus() == PeerDiscoveryStatus.BONDED);

    // Use additional agent to exchange messages with agent
    final MockPeerDiscoveryAgent testAgent = helper.startDiscoveryAgent();

    // Send a PING so we can exchange messages with the latter agent.
    Packet packet = helper.createPingPacket(testAgent, agent);
    helper.sendMessageBetweenAgents(testAgent, agent, packet);

    // Send a FIND_NEIGHBORS message.
    packet =
        Packet.create(
            PacketType.FIND_NEIGHBORS,
            FindNeighborsPacketData.create(otherAgents.get(0).getAdvertisedPeer().get().getId()),
            testAgent.getKeyPair());
    helper.sendMessageBetweenAgents(testAgent, agent, packet);

    // Check response packet
    List<IncomingPacket> incomingPackets =
        testAgent.getIncomingPackets().stream()
            .filter(p -> p.packet.getType().equals(PacketType.NEIGHBORS))
            .collect(toList());
    assertThat(incomingPackets.size()).isEqualTo(1);
    IncomingPacket neighborsPacket = incomingPackets.get(0);
    assertThat(neighborsPacket.fromAgent).isEqualTo(agent);

    // Assert that we only received 16 items.
    final NeighborsPacketData neighbors =
        neighborsPacket.packet.getPacketData(NeighborsPacketData.class).get();
    assertThat(neighbors).isNotNull();
    assertThat(neighbors.getNodes()).hasSize(16);

    // Assert that after removing those 16 items we're left with either 4 or 5.
    // If we are left with 5, the test peer was returned as an item, assert that this is the case.
    otherPeers.removeAll(neighbors.getNodes());
    assertThat(otherPeers.size()).isBetween(4, 5);
    if (otherPeers.size() == 5) {
      assertThat(neighbors.getNodes()).contains(testAgent.getAdvertisedPeer().get());
    }
  }

  @Test
  public void shouldEvictPeerOnDisconnect() {
    final MockPeerDiscoveryAgent peerDiscoveryAgent1 = helper.startDiscoveryAgent();
    peerDiscoveryAgent1.start(BROADCAST_TCP_PORT).join();
    final DiscoveryPeer peer = peerDiscoveryAgent1.getAdvertisedPeer().get();

    final MockPeerDiscoveryAgent peerDiscoveryAgent2 = helper.startDiscoveryAgent(peer);
    peerDiscoveryAgent2.start(BROADCAST_TCP_PORT).join();

    assertThat(peerDiscoveryAgent2.getPeers().collect(toList()).size()).isEqualTo(1);

    final PeerConnection peerConnection = createAnonymousPeerConnection(peer.getId());
    peerDiscoveryAgent2.onDisconnect(peerConnection, DisconnectReason.REQUESTED, true);

    assertThat(peerDiscoveryAgent2.getPeers().collect(toList()).size()).isEqualTo(0);
  }

  @Test
  public void doesNotBlacklistPeerForNormalDisconnect() {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final PeerConnection wirePeer = createAnonymousPeerConnection(otherNode.getId());

    // Bond to peer
    bondViaIncomingPing(agent, otherNode);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect with innocuous reason
    blacklist.onDisconnect(wirePeer, DisconnectReason.TOO_MANY_PEERS, false);
    agent.onDisconnect(wirePeer, DisconnectReason.TOO_MANY_PEERS, false);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, otherNode);

    // Check peer was allowed to connect
    assertThat(agent.getPeers()).hasSize(1);
  }

  protected void bondViaIncomingPing(
      final MockPeerDiscoveryAgent agent, final MockPeerDiscoveryAgent otherNode) {
    Packet pingPacket = helper.createPingPacket(otherNode, agent);
    helper.sendMessageBetweenAgents(otherNode, agent, pingPacket);
  }

  @Test
  public void blacklistPeerForBadBehavior() {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final PeerConnection wirePeer = createAnonymousPeerConnection(otherNode.getId());

    // Bond to peer
    bondViaIncomingPing(agent, otherNode);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect with problematic reason
    blacklist.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, false);
    agent.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, false);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, otherNode);

    // Check peer was not allowed to connect
    assertThat(agent.getPeers()).hasSize(0);
  }

  @Test
  public void doesNotBlacklistPeerForOurBadBehavior() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final PeerConnection wirePeer = createAnonymousPeerConnection(otherNode.getId());

    // Bond to peer
    bondViaIncomingPing(agent, otherNode);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect with problematic reason
    blacklist.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, true);
    agent.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, true);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, otherNode);

    // Check peer was allowed to connect
    assertThat(agent.getPeers()).hasSize(1);
  }

  @Test
  public void blacklistIncompatiblePeer() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final PeerConnection wirePeer = createAnonymousPeerConnection(otherNode.getId());

    // Bond to peer
    bondViaIncomingPing(agent, otherNode);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect
    blacklist.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, false);
    agent.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, false);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, otherNode);

    // Check peer was not allowed to connect
    assertThat(agent.getPeers()).hasSize(0);
  }

  @Test
  public void blacklistIncompatiblePeerWhoIssuesDisconnect() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final PeerConnection wirePeer = createAnonymousPeerConnection(otherNode.getId());

    // Bond to peer
    bondViaIncomingPing(agent, otherNode);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect
    blacklist.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, true);
    agent.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, true);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, otherNode);

    // Check peer was not allowed to connect
    assertThat(agent.getPeers()).hasSize(0);
  }

  @Test
  public void shouldBeActiveWhenConfigIsTrue() {
    AgentBuilder agentBuilder = helper.agentBuilder().active(true);
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(agentBuilder);

    assertThat(agent.isActive()).isTrue();
  }

  @Test
  public void shouldNotBeActiveWhenConfigIsFalse() {
    AgentBuilder agentBuilder = helper.agentBuilder().active(false);
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(agentBuilder);

    assertThat(agent.isActive()).isFalse();
  }

  private PeerConnection createAnonymousPeerConnection(final BytesValue id) {
    PeerConnection conn = mock(PeerConnection.class);
    PeerInfo peerInfo = new PeerInfo(0, null, null, 0, id);
    when(conn.getPeerInfo()).thenReturn(peerInfo);
    return conn;
  }
}
