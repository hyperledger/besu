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
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PingPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PongPacketData;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class PeerDiscoveryBondingTest extends AbstractPeerDiscoveryTest {

  @Test
  public void pongSentUponPing() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList());

    // Start a test peer and send a PING packet to the agent under test.
    final DiscoveryTestSocket discoveryTestSocket = startTestSocket();

    final PingPacketData ping =
        PingPacketData.create(
            discoveryTestSocket.getPeer().getEndpoint(), agent.getAdvertisedPeer().getEndpoint());
    final Packet packet = Packet.create(PacketType.PING, ping, discoveryTestSocket.getKeyPair());
    discoveryTestSocket.sendToAgent(agent, packet);

    final Packet pongPacket = discoveryTestSocket.getIncomingPackets().poll(10, TimeUnit.SECONDS);
    assertThat(pongPacket.getType()).isEqualTo(PacketType.PONG);
    assertThat(pongPacket.getPacketData(PongPacketData.class)).isPresent();

    final PongPacketData pong = pongPacket.getPacketData(PongPacketData.class).get();
    assertThat(pong.getTo()).isEqualTo(discoveryTestSocket.getPeer().getEndpoint());

    // The agent considers the test peer BONDED.
    assertThat(agent.getPeers()).hasSize(1);
    assertThat(agent.getPeers()).allMatch(p -> p.getStatus() == PeerDiscoveryStatus.BONDED);
  }

  @Test
  public void neighborsPacketNotSentUnlessBonded() throws InterruptedException {
    // Start an agent.
    final PeerDiscoveryAgent agent = startDiscoveryAgent(emptyList());

    // Start a test peer that will send a FIND_NEIGHBORS to the agent under test. It should be
    // ignored because
    // we haven't bonded.
    final DiscoveryTestSocket discoveryTestSocket = startTestSocket();
    final FindNeighborsPacketData data =
        FindNeighborsPacketData.create(discoveryTestSocket.getPeer().getId());
    Packet packet =
        Packet.create(PacketType.FIND_NEIGHBORS, data, discoveryTestSocket.getKeyPair());
    discoveryTestSocket.sendToAgent(agent, packet);

    // No responses received in 2 seconds.
    final Packet incoming = discoveryTestSocket.getIncomingPackets().poll(2, TimeUnit.SECONDS);
    assertThat(incoming).isNull();

    // Create and dispatch a PING packet.
    final PingPacketData ping =
        PingPacketData.create(
            discoveryTestSocket.getPeer().getEndpoint(), agent.getAdvertisedPeer().getEndpoint());
    packet = Packet.create(PacketType.PING, ping, discoveryTestSocket.getKeyPair());
    discoveryTestSocket.sendToAgent(agent, packet);

    // Now we received a PONG.
    final Packet pongPacket = discoveryTestSocket.getIncomingPackets().poll(2, TimeUnit.SECONDS);
    assertThat(pongPacket.getType()).isEqualTo(PacketType.PONG);
    assertThat(pongPacket.getPacketData(PongPacketData.class)).isPresent();

    final PongPacketData pong = pongPacket.getPacketData(PongPacketData.class).get();
    assertThat(pong.getTo()).isEqualTo(discoveryTestSocket.getPeer().getEndpoint());

    // No more packets.
    assertThat(discoveryTestSocket.getIncomingPackets()).hasSize(0);
  }
}
