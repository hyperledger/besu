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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.BlockingAsyncExecutor;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockTimerUtil;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerTable;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PingPacketData;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.Subscribers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class PeerDiscoveryTimestampsTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void lastSeenAndFirstDiscoveredTimestampsUpdatedOnMessage() {
    // peer[0] => controller // peer[1] => sender
    final List<KeyPair> keypairs = PeerDiscoveryTestHelper.generateKeyPairs(2);
    final List<DiscoveryPeer> peers = helper.createDiscoveryPeers(keypairs);

    final MockPeerDiscoveryAgent agent = mock(MockPeerDiscoveryAgent.class);
    when(agent.getAdvertisedPeer()).thenReturn(Optional.of(peers.get(0)));
    final DiscoveryPeer localPeer = peers.get(0);
    final KeyPair localKeyPair = keypairs.get(0);

    final PeerDiscoveryController controller =
        PeerDiscoveryController.builder()
            .keypair(localKeyPair)
            .localPeer(localPeer)
            .peerTable(new PeerTable(agent.getAdvertisedPeer().get().getId()))
            .timerUtil(new MockTimerUtil())
            .workerExecutor(new BlockingAsyncExecutor())
            .tableRefreshIntervalMs(TimeUnit.HOURS.toMillis(1))
            .peerBondedObservers(Subscribers.create())
            .metricsSystem(new NoOpMetricsSystem())
            .build();
    controller.start();

    final PingPacketData ping =
        PingPacketData.create(peers.get(1).getEndpoint(), peers.get(0).getEndpoint());
    final Packet packet = Packet.create(PacketType.PING, ping, keypairs.get(1));

    controller.onMessage(packet, peers.get(1));

    final AtomicLong lastSeen = new AtomicLong();
    final AtomicLong firstDiscovered = new AtomicLong();

    assertThat(controller.streamDiscoveredPeers()).hasSize(1);

    DiscoveryPeer p = controller.streamDiscoveredPeers().iterator().next();
    assertThat(p.getLastSeen()).isGreaterThan(0);
    assertThat(p.getFirstDiscovered()).isGreaterThan(0);

    lastSeen.set(p.getLastSeen());
    firstDiscovered.set(p.getFirstDiscovered());

    controller.onMessage(packet, peers.get(1));

    assertThat(controller.streamDiscoveredPeers()).hasSize(1);

    p = controller.streamDiscoveredPeers().iterator().next();
    assertThat(p.getLastSeen()).isGreaterThan(lastSeen.get());
    assertThat(p.getFirstDiscovered()).isEqualTo(firstDiscovered.get());
  }

  @Test
  public void lastContactedTimestampUpdatedOnOutboundMessage() {
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.streamDiscoveredPeers()).hasSize(0);

    // Start a test peer and send a PING packet to the agent under test.
    final MockPeerDiscoveryAgent testAgent = helper.startDiscoveryAgent();
    final Packet ping = helper.createPingPacket(testAgent, agent);
    helper.sendMessageBetweenAgents(testAgent, agent, ping);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);

    final AtomicLong lastContacted = new AtomicLong();
    final AtomicLong lastSeen = new AtomicLong();
    final AtomicLong firstDiscovered = new AtomicLong();

    DiscoveryPeer peer = agent.streamDiscoveredPeers().iterator().next();
    final long lc = peer.getLastContacted();
    final long ls = peer.getLastSeen();
    final long fd = peer.getFirstDiscovered();

    assertThat(lc).isGreaterThan(0);
    assertThat(ls).isGreaterThan(0);
    assertThat(fd).isGreaterThan(0);

    lastContacted.set(lc);
    lastSeen.set(ls);
    firstDiscovered.set(fd);

    // Send another packet and ensure that timestamps are updated accordingly.
    helper.sendMessageBetweenAgents(testAgent, agent, ping);

    peer = agent.streamDiscoveredPeers().iterator().next();

    assertThat(peer.getLastContacted()).isGreaterThan(lastContacted.get());
    assertThat(peer.getLastSeen()).isGreaterThan(lastSeen.get());
    assertThat(peer.getFirstDiscovered()).isEqualTo(firstDiscovered.get());
  }
}
