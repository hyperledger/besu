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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryTestHelper.AgentBuilder;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent.IncomingPacket;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.NeighborsPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsBlacklist;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;

public class PeerDiscoveryAgentTest {

  private static final int BROADCAST_TCP_PORT = 30303;
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void createAgentWithInvalidBootnodes() {
    final EnodeURL invalidBootnode =
        EnodeURL.builder()
            .nodeId(Peer.randomId())
            .ipAddress("127.0.0.1")
            .listeningPort(30303)
            .disableDiscovery()
            .build();

    assertThatThrownBy(
            () -> helper.createDiscoveryAgent(helper.agentBuilder().bootnodes(invalidBootnode)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid bootnodes")
        .hasMessageContaining("Bootnodes must have discovery enabled");
  }

  @Test
  public void neighborsPacketFromUnbondedPeerIsDropped() {
    // Start an agent with no bootstrap peers.
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.streamDiscoveredPeers()).isEmpty();

    // Start a test peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();

    // Generate an out-of-band NEIGHBORS message.
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(5);
    final NeighborsPacketData data = NeighborsPacketData.create(peers);
    final Packet packet = Packet.create(PacketType.NEIGHBORS, data, otherNode.getKeyPair());
    helper.sendMessageBetweenAgents(otherNode, agent, packet);

    assertThat(agent.streamDiscoveredPeers()).isEmpty();
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
    assertThat(agent.streamDiscoveredPeers()).contains(otherPeers.toArray(new DiscoveryPeer[20]));
    assertThat(agent.streamDiscoveredPeers())
        .allMatch(p -> p.getStatus() == PeerDiscoveryStatus.BONDED);

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
    final List<IncomingPacket> incomingPackets =
        testAgent.getIncomingPackets().stream()
            .filter(p -> p.packet.getType().equals(PacketType.NEIGHBORS))
            .collect(toList());
    assertThat(incomingPackets.size()).isEqualTo(1);
    final IncomingPacket neighborsPacket = incomingPackets.get(0);
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
  public void shouldEvictPeerWhenPermissionsRevoked() {
    final PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
    final MockPeerDiscoveryAgent peerDiscoveryAgent1 = helper.startDiscoveryAgent();
    peerDiscoveryAgent1.start(BROADCAST_TCP_PORT).join();
    final DiscoveryPeer peer = peerDiscoveryAgent1.getAdvertisedPeer().get();

    final MockPeerDiscoveryAgent peerDiscoveryAgent2 =
        helper.startDiscoveryAgent(
            helper.agentBuilder().peerPermissions(blacklist).bootstrapPeers(peer));
    peerDiscoveryAgent2.start(BROADCAST_TCP_PORT).join();

    assertThat(peerDiscoveryAgent2.streamDiscoveredPeers().collect(toList()).size()).isEqualTo(1);

    blacklist.add(peer);

    assertThat(peerDiscoveryAgent2.streamDiscoveredPeers().collect(toList()).size()).isEqualTo(0);
  }

  @Test
  public void peerTable_allowPeer() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(false);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE)))
        .thenReturn(true);

    agent.start(999);
    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
  }

  @Test
  public void peerTable_disallowPeer() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE)))
        .thenReturn(false);

    agent.start(999);
    assertThat(agent.streamDiscoveredPeers()).hasSize(0);
  }

  @Test
  public void bonding_allowIncomingBonding() {
    // Start an agent with no bootstrap peers.
    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), peerPermissions);
    final Peer localNode = agent.getAdvertisedPeer().get();

    // Setup peer and permissions
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final Peer remotePeer = otherNode.getAdvertisedPeer().get();
    when(peerPermissions.isPermitted(eq(localNode), any(), any())).thenReturn(false);
    when(peerPermissions.isPermitted(
            eq(localNode), eq(remotePeer), eq(Action.DISCOVERY_ACCEPT_INBOUND_BONDING)))
        .thenReturn(true);

    // Bond
    bondViaIncomingPing(agent, otherNode);

    // Check that peer received a return pong
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).hasSize(1);
    final IncomingPacket firstMsg = remoteIncomingPackets.get(0);
    assertThat(firstMsg.packet.getType()).isEqualTo(PacketType.PONG);
    assertThat(firstMsg.fromAgent).isEqualTo(agent);
  }

  @Test
  public void bonding_disallowIncomingBonding() {
    // Start an agent with no bootstrap peers.
    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), peerPermissions);
    final Peer localNode = agent.getAdvertisedPeer().get();

    // Setup peer and permissions
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final Peer remotePeer = otherNode.getAdvertisedPeer().get();
    when(peerPermissions.isPermitted(eq(localNode), any(), any())).thenReturn(true);
    when(peerPermissions.isPermitted(
            eq(localNode), eq(remotePeer), eq(Action.DISCOVERY_ACCEPT_INBOUND_BONDING)))
        .thenReturn(false);

    // Bond
    bondViaIncomingPing(agent, otherNode);

    // Check peer was not allowed to connect
    assertThat(agent.streamDiscoveredPeers()).hasSize(0);
    // Check that peer did not receive a return pong
    assertThat(otherNode.getIncomingPackets()).isEmpty();
  }

  @Test
  public void bonding_allowOutgoingBonding() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(false);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_OUTBOUND_BONDING)))
        .thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE)))
        .thenReturn(true);

    agent.start(999);

    // Check peer was allowed
    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    // Check that peer received a return ping
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).hasSize(1);
    final IncomingPacket firstMsg = remoteIncomingPackets.get(0);
    assertThat(firstMsg.packet.getType()).isEqualTo(PacketType.PING);
    assertThat(firstMsg.fromAgent).isEqualTo(agent);
  }

  @Test
  public void bonding_disallowOutgoingBonding() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(false);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_OUTBOUND_BONDING)))
        .thenReturn(false);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE)))
        .thenReturn(true);

    agent.start(999);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).isEmpty();
  }

  /**
   * These tests simulates the case where a node crashes then comes back up with a new ip address or
   * listening port.
   */
  @Test
  public void bonding_simulatePeerRestartingWithNewEndpoint_updatedPort() {
    simulatePeerRestartingOnDifferentEndpoint(false, true);
  }

  @Test
  public void bonding_simulatePeerRestartingWithNewEndpoint_updatedHost() {
    simulatePeerRestartingOnDifferentEndpoint(true, false);
  }

  @Test
  public void bonding_simulatePeerRestartingWithNewEndpoint_updatedHostAndPort() {
    simulatePeerRestartingOnDifferentEndpoint(true, true);
  }

  public void simulatePeerRestartingOnDifferentEndpoint(
      final boolean updateHost, final boolean updatePort) {
    // Setup peer
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent();
    final DiscoveryPeer agentPeer = agent.getAdvertisedPeer().get();

    final KeyPair remoteKeyPair = SECP256K1.KeyPair.generate();
    final String remoteIp = "1.2.3.4";
    final MockPeerDiscoveryAgent remoteAgent =
        helper.createDiscoveryAgent(
            helper
                .agentBuilder()
                .keyPair(remoteKeyPair)
                .advertisedHost(remoteIp)
                .bootstrapPeers(agentPeer));

    agent.start(999);
    remoteAgent.start(888);
    final DiscoveryPeer remotePeer = remoteAgent.getAdvertisedPeer().get();

    // Remote agent should have bonded with agent
    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    assertThat(agent.streamDiscoveredPeers()).contains(remoteAgent.getAdvertisedPeer().get());

    // Create a new remote agent with same id, and new endpoint
    remoteAgent.stop();
    final int newPort = updatePort ? 0 : remotePeer.getEndpoint().getUdpPort();
    final String newIp = updateHost ? "1.2.3.5" : remoteIp;
    final MockPeerDiscoveryAgent updatedRemoteAgent =
        helper.createDiscoveryAgent(
            helper
                .agentBuilder()
                .keyPair(remoteKeyPair)
                .advertisedHost(newIp)
                .bindPort(newPort)
                .bootstrapPeers(agentPeer));
    updatedRemoteAgent.start(889);
    final DiscoveryPeer updatedRemotePeer = updatedRemoteAgent.getAdvertisedPeer().get();

    // Sanity check
    assertThat(
            updatedRemotePeer.getEndpoint().getUdpPort() == remotePeer.getEndpoint().getUdpPort())
        .isEqualTo(!updatePort);
    assertThat(updatedRemotePeer.getEndpoint().getHost().equals(remotePeer.getEndpoint().getHost()))
        .isEqualTo(!updateHost);
    assertThat(updatedRemotePeer.getId()).isEqualTo(remotePeer.getId());

    // Check that our restarted agent receives a PONG response
    final List<IncomingPacket> incomingPackets = updatedRemoteAgent.getIncomingPackets();
    assertThat(incomingPackets).hasSizeGreaterThan(0);
    final long pongCount =
        incomingPackets.stream()
            .filter(packet -> packet.fromAgent.equals(agent))
            .filter(packet -> packet.packet.getType().equals(PacketType.PONG))
            .count();
    assertThat(pongCount).isGreaterThan(0);

    // Check that agent has an endpoint matching the restarted node
    final List<DiscoveryPeer> matchingPeers =
        agent
            .streamDiscoveredPeers()
            .filter(peer -> peer.getId().equals(updatedRemotePeer.getId()))
            .collect(toList());
    // We should have only one peer matching this id
    assertThat(matchingPeers.size()).isEqualTo(1);
    final DiscoveryPeer discoveredPeer = matchingPeers.get(0);
    assertThat(discoveredPeer.getEndpoint().getUdpPort())
        .isEqualTo(updatedRemotePeer.getEndpoint().getUdpPort());
    assertThat(discoveredPeer.getEndpoint().getHost())
        .isEqualTo(updatedRemotePeer.getEndpoint().getHost());
    // Check endpoint is consistent with enodeURL
    assertThat(discoveredPeer.getEnodeURL().getDiscoveryPortOrZero())
        .isEqualTo(updatedRemotePeer.getEndpoint().getUdpPort());
    assertThat(discoveredPeer.getEnodeURL().getListeningPortOrZero())
        .isEqualTo(updatedRemotePeer.getEndpoint().getFunctionalTcpPort());
    assertThat(discoveredPeer.getEnodeURL().getIpAsString())
        .isEqualTo(updatedRemotePeer.getEndpoint().getHost());
  }

  @Test
  public void neighbors_allowOutgoingRequest() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(false);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE)))
        .thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_OUTBOUND_BONDING)))
        .thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST)))
        .thenReturn(true);

    agent.start(999);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).hasSize(2);
    // Peer should get a ping
    final IncomingPacket firstMsg = remoteIncomingPackets.get(0);
    assertThat(firstMsg.packet.getType()).isEqualTo(PacketType.PING);
    assertThat(firstMsg.fromAgent).isEqualTo(agent);
    // Then a neighbors request
    final IncomingPacket secondMsg = remoteIncomingPackets.get(1);
    assertThat(secondMsg.packet.getType()).isEqualTo(PacketType.FIND_NEIGHBORS);
    assertThat(secondMsg.fromAgent).isEqualTo(agent);
  }

  @Test
  public void neighbors_disallowOutgoingRequest() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST)))
        .thenReturn(false);

    agent.start(999);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).hasSize(1);
    // Peer should get a ping
    final IncomingPacket firstMsg = remoteIncomingPackets.get(0);
    assertThat(firstMsg.packet.getType()).isEqualTo(PacketType.PING);
    assertThat(firstMsg.fromAgent).isEqualTo(agent);
  }

  @Test
  public void neighbors_allowIncomingRequest() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(false);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_IN_PEER_TABLE)))
        .thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_ALLOW_OUTBOUND_BONDING)))
        .thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_SERVE_INBOUND_NEIGHBORS_REQUEST)))
        .thenReturn(true);

    agent.start(999);

    // Send request for neighbors
    requestNeighbors(otherNode, agent);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).hasSize(2);

    // Peer should get a ping
    final IncomingPacket firstMsg = remoteIncomingPackets.get(0);
    assertThat(firstMsg.packet.getType()).isEqualTo(PacketType.PING);
    assertThat(firstMsg.fromAgent).isEqualTo(agent);
    // Then a neighbors response
    final IncomingPacket secondMsg = remoteIncomingPackets.get(1);
    assertThat(secondMsg.packet.getType()).isEqualTo(PacketType.NEIGHBORS);
    assertThat(secondMsg.fromAgent).isEqualTo(agent);
  }

  @Test
  public void neighbors_disallowIncomingRequest() {
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final DiscoveryPeer remotePeer = otherNode.getAdvertisedPeer().get();

    final PeerPermissions peerPermissions = mock(PeerPermissions.class);
    final MockPeerDiscoveryAgent agent =
        helper.createDiscoveryAgent(
            helper.agentBuilder().bootstrapPeers(remotePeer).peerPermissions(peerPermissions));

    when(peerPermissions.isPermitted(any(), any(), any())).thenReturn(true);
    when(peerPermissions.isPermitted(
            any(), eq(remotePeer), eq(Action.DISCOVERY_SERVE_INBOUND_NEIGHBORS_REQUEST)))
        .thenReturn(false);

    agent.start(999);

    // Send request for neighbors
    requestNeighbors(otherNode, agent);

    // Peer should get a ping and a neighbors request, but no neighbors response
    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    List<IncomingPacket> remoteIncomingPackets = otherNode.getIncomingPackets();
    assertThat(remoteIncomingPackets).hasSize(2);
    // Peer should get a ping
    final IncomingPacket firstMsg = remoteIncomingPackets.get(0);
    assertThat(firstMsg.packet.getType()).isEqualTo(PacketType.PING);
    assertThat(firstMsg.fromAgent).isEqualTo(agent);
    // And a request FOR neighbors, but no response to its neighbors request
    final IncomingPacket secondMsg = remoteIncomingPackets.get(1);
    assertThat(secondMsg.packet.getType()).isEqualTo(PacketType.FIND_NEIGHBORS);
    assertThat(secondMsg.fromAgent).isEqualTo(agent);
  }

  @Test
  public void shouldBeActiveWhenConfigIsTrue() {
    final AgentBuilder agentBuilder = helper.agentBuilder().active(true);
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(agentBuilder);

    assertThat(agent.isActive()).isTrue();
  }

  @Test
  public void shouldNotBeActiveWhenConfigIsFalse() {
    final AgentBuilder agentBuilder = helper.agentBuilder().active(false);
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(agentBuilder);

    assertThat(agent.isActive()).isFalse();
  }

  protected void bondViaIncomingPing(
      final MockPeerDiscoveryAgent agent, final MockPeerDiscoveryAgent otherNode) {
    final Packet pingPacket = helper.createPingPacket(otherNode, agent);
    helper.sendMessageBetweenAgents(otherNode, agent, pingPacket);
  }

  protected void requestNeighbors(
      final MockPeerDiscoveryAgent fromAgent, final MockPeerDiscoveryAgent toAgent) {
    final FindNeighborsPacketData data = FindNeighborsPacketData.create(Peer.randomId());
    final Packet packet = Packet.create(PacketType.FIND_NEIGHBORS, data, fromAgent.getKeyPair());
    helper.sendMessageBetweenAgents(fromAgent, toAgent, packet);
  }
}
