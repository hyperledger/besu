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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent.IncomingPacket;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PongPacketData;
import tech.pegasys.pantheon.testutil.TestClock;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;

public class PeerDiscoveryBondingTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void pongSentUponPing() {
    // Start an agent with no bootstrap peers.
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());

    // Start a test peer and send a PING packet to the agent under test.
    final MockPeerDiscoveryAgent otherAgent = helper.startDiscoveryAgent();
    final Packet ping = helper.createPingPacket(otherAgent, agent);
    helper.sendMessageBetweenAgents(otherAgent, agent, ping);

    final List<IncomingPacket> otherAgentIncomingPongs =
        otherAgent.getIncomingPackets().stream()
            .filter(p -> p.packet.getType().equals(PacketType.PONG))
            .collect(Collectors.toList());
    assertThat(otherAgentIncomingPongs.size()).isEqualTo(1);

    final PongPacketData pong =
        otherAgentIncomingPongs.get(0).packet.getPacketData(PongPacketData.class).get();
    assertThat(pong.getTo()).isEqualTo(otherAgent.getAdvertisedPeer().get().getEndpoint());

    // The agent considers the test peer BONDED.
    assertThat(agent.streamDiscoveredPeers()).hasSize(1);
    assertThat(agent.streamDiscoveredPeers())
        .allMatch(p -> p.getStatus() == PeerDiscoveryStatus.BONDED);
  }

  @Test
  public void neighborsPacketNotSentUnlessBonded() throws InterruptedException {
    // Start an agent.
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(emptyList());

    // Start a test peer that will send a FIND_NEIGHBORS to the agent under test. It should be
    // ignored because
    // we haven't bonded.
    final MockPeerDiscoveryAgent otherNode = helper.startDiscoveryAgent();
    final FindNeighborsPacketData data =
        FindNeighborsPacketData.create(otherNode.getId(), TestClock.fixed());
    final Packet packet = Packet.create(PacketType.FIND_NEIGHBORS, data, otherNode.getKeyPair());
    helper.sendMessageBetweenAgents(otherNode, agent, packet);

    // No responses received
    final List<IncomingPacket> incoming = otherNode.getIncomingPackets();
    assertThat(incoming.size()).isEqualTo(0);

    // Create and dispatch a PING packet.
    final Packet ping = helper.createPingPacket(otherNode, agent);
    helper.sendMessageBetweenAgents(otherNode, agent, ping);

    // Now we received a PONG.
    final List<IncomingPacket> incomingPongs =
        otherNode.getIncomingPackets().stream()
            .filter(p -> p.packet.getType().equals(PacketType.PONG))
            .collect(Collectors.toList());
    assertThat(incomingPongs.size()).isEqualTo(1);
    final Optional<PongPacketData> maybePongData =
        incomingPongs.get(0).packet.getPacketData(PongPacketData.class);
    assertThat(maybePongData).isPresent();
    assertThat(maybePongData.get().getTo())
        .isEqualTo(otherNode.getAdvertisedPeer().get().getEndpoint());

    // No more packets.
    assertThat(otherNode.getIncomingPackets()).hasSize(0);
  }
}
