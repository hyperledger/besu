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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper.AgentBuilder;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent.IncomingPacket;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.NeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissionsBlacklist;
import tech.pegasys.pantheon.util.enode.EnodeURL;

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

  protected void bondViaIncomingPing(
      final MockPeerDiscoveryAgent agent, final MockPeerDiscoveryAgent otherNode) {
    final Packet pingPacket = helper.createPingPacket(otherNode, agent);
    helper.sendMessageBetweenAgents(otherNode, agent, pingPacket);
  }

  @Test
  public void dontBondWithNonPermittedPeer() {
    // Start an agent with no bootstrap peers.
    final PeerPermissionsBlacklist blacklist = PeerPermissionsBlacklist.create();
    final MockPeerDiscoveryAgent agent =
        helper.startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();

    blacklist.add(otherNode.getId());

    // Bond
    bondViaIncomingPing(agent, otherNode);

    // Check peer was not allowed to connect
    assertThat(agent.streamDiscoveredPeers()).hasSize(0);
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
}
