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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerDiscoveryController;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerTable;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PingPacketData;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.Vertx;
import org.junit.Test;

public class PeerDiscoveryTimestampsTest extends AbstractPeerDiscoveryTest {

  @Test
  public void lastSeenAndFirstDiscoveredTimestampsUpdatedOnMessage() {
    // peer[0] => controller // peer[1] => sender
    final SECP256K1.KeyPair[] keypairs = PeerDiscoveryTestHelper.generateKeyPairs(2);
    final DiscoveryPeer[] peers = PeerDiscoveryTestHelper.generateDiscoveryPeers(keypairs);

    final PeerDiscoveryAgent agent = mock(PeerDiscoveryAgent.class);
    when(agent.getAdvertisedPeer()).thenReturn(peers[0]);

    final PeerDiscoveryController controller =
        new PeerDiscoveryController(
            mock(Vertx.class),
            agent,
            new PeerTable(agent.getAdvertisedPeer().getId()),
            Collections.emptyList(),
            TimeUnit.HOURS.toMillis(1),
            () -> true,
            new PeerBlacklist());
    controller.start();

    final PingPacketData ping =
        PingPacketData.create(peers[1].getEndpoint(), peers[0].getEndpoint());
    final Packet packet = Packet.create(PacketType.PING, ping, keypairs[1]);

    controller.onMessage(packet, peers[1]);

    final AtomicLong lastSeen = new AtomicLong();
    final AtomicLong firstDiscovered = new AtomicLong();

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(controller.getPeers()).hasSize(1);

              final DiscoveryPeer p = controller.getPeers().iterator().next();
              assertThat(p.getLastSeen()).isGreaterThan(0);
              assertThat(p.getFirstDiscovered()).isGreaterThan(0);

              lastSeen.set(p.getLastSeen());
              firstDiscovered.set(p.getFirstDiscovered());
            });

    controller.onMessage(packet, peers[1]);

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(controller.getPeers()).hasSize(1);

              final DiscoveryPeer p = controller.getPeers().iterator().next();
              assertThat(p.getLastSeen()).isGreaterThan(lastSeen.get());
              assertThat(p.getFirstDiscovered()).isEqualTo(firstDiscovered.get());
            });
  }

  @Test
  public void lastContactedTimestampUpdatedOnOutboundMessage() {
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getPeers()).hasSize(0);

    // Start a test peer and send a PING packet to the agent under test.
    final DiscoveryTestSocket discoveryTestSocket = startTestSocket();

    final PingPacketData ping =
        PingPacketData.create(
            discoveryTestSocket.getPeer().getEndpoint(), agent.getAdvertisedPeer().getEndpoint());
    final Packet packet = Packet.create(PacketType.PING, ping, discoveryTestSocket.getKeyPair());
    discoveryTestSocket.sendToAgent(agent, packet);

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(agent.getPeers()).hasSize(1));

    final AtomicLong lastContacted = new AtomicLong();
    final AtomicLong lastSeen = new AtomicLong();
    final AtomicLong firstDiscovered = new AtomicLong();

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              final DiscoveryPeer peer = agent.getPeers().iterator().next();
              final long lc = peer.getLastContacted();
              final long ls = peer.getLastSeen();
              final long fd = peer.getFirstDiscovered();

              assertThat(lc).isGreaterThan(0);
              assertThat(ls).isGreaterThan(0);
              assertThat(fd).isGreaterThan(0);

              lastContacted.set(lc);
              lastSeen.set(ls);
              firstDiscovered.set(fd);
            });

    // Send another packet and ensure that timestamps are updated accordingly.
    discoveryTestSocket.sendToAgent(agent, packet);

    await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              final DiscoveryPeer peer = agent.getPeers().iterator().next();

              assertThat(peer.getLastContacted()).isGreaterThan(lastContacted.get());
              assertThat(peer.getLastSeen()).isGreaterThan(lastSeen.get());
              assertThat(peer.getFirstDiscovered()).isEqualTo(firstDiscovered.get());
            });
  }
}
