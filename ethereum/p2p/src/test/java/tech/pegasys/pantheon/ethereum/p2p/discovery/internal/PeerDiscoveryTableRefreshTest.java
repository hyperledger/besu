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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PeerDiscoveryTableRefreshTest {
  private final Vertx vertx = spy(Vertx.vertx());

  @Test
  public void tableRefreshSingleNode() {
    final SECP256K1.KeyPair[] keypairs = PeerDiscoveryTestHelper.generateKeyPairs(2);
    final DiscoveryPeer[] peers = PeerDiscoveryTestHelper.generateDiscoveryPeers(keypairs);

    final PeerDiscoveryAgent agent = mock(PeerDiscoveryAgent.class);
    when(agent.getAdvertisedPeer()).thenReturn(peers[0]);

    // Create and start the PeerDiscoveryController, setting the refresh interval to something
    // small.
    final PeerDiscoveryController controller =
        new PeerDiscoveryController(
            vertx,
            agent,
            new PeerTable(agent.getAdvertisedPeer().getId()),
            emptyList(),
            100,
            () -> true,
            new PeerBlacklist());
    controller.start();

    // Send a PING, so as to add a Peer in the controller.
    final PingPacketData ping =
        PingPacketData.create(peers[1].getEndpoint(), peers[0].getEndpoint());
    final Packet packet = Packet.create(PacketType.PING, ping, keypairs[1]);
    controller.onMessage(packet, peers[1]);

    // Wait until the controller has added the newly found peer.
    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(controller.getPeers()).hasSize(1));

    // As the controller performs refreshes, it'll send FIND_NEIGHBORS packets with random target
    // IDs every time.
    // We capture the packets so that we can later assert on them.
    // Within 1000ms, there should be ~10 packets. But let's be less ambitious and expect at least
    // 5.
    final ArgumentCaptor<PacketData> packetDataCaptor = ArgumentCaptor.forClass(PacketData.class);
    verify(agent, timeout(1000).atLeast(5))
        .sendPacket(eq(peers[1]), eq(PacketType.FIND_NEIGHBORS), packetDataCaptor.capture());

    // Assert that all packets were FIND_NEIGHBORS packets.
    final List<BytesValue> targets = new ArrayList<>();
    for (final PacketData data : packetDataCaptor.getAllValues()) {
      assertThat(data).isExactlyInstanceOf(FindNeighborsPacketData.class);
      final FindNeighborsPacketData fnpd = (FindNeighborsPacketData) data;
      targets.add(fnpd.getTarget());
    }

    assertThat(targets.size()).isGreaterThanOrEqualTo(5);

    // All targets are unique.
    assertThat(targets.size()).isEqualTo(new HashSet<>(targets).size());
  }
}
