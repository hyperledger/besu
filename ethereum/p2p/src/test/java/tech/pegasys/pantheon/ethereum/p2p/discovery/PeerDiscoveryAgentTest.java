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

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.FindNeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.NeighborsPacketData;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PingPacketData;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import org.junit.Test;

public class PeerDiscoveryAgentTest extends AbstractPeerDiscoveryTest {

  @Test
  public void neighborsPacketFromUnbondedPeerIsDropped() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getPeers()).isEmpty();

    // Start a test peer and send a PING packet to the agent under test.
    final DiscoveryTestSocket discoveryTestSocket = startTestSocket();

    // Peer is unbonded, as it has not replied with a PONG.

    // Generate an out-of-band NEIGHBORS message.
    final DiscoveryPeer[] peers =
        PeerDiscoveryTestHelper.generatePeers(PeerDiscoveryTestHelper.generateKeyPairs(5));
    final NeighborsPacketData data = NeighborsPacketData.create(Arrays.asList(peers));
    final Packet packet =
        Packet.create(PacketType.NEIGHBORS, data, discoveryTestSocket.getKeyPair());
    discoveryTestSocket.sendToAgent(agent, packet);

    TimeUnit.SECONDS.sleep(1);
    assertThat(agent.getPeers()).isEmpty();
  }

  @Test
  public void neighborsPacketLimited() {
    // Start 20 agents with no bootstrap peers.
    final List<PeerDiscoveryAgent> agents = startDiscoveryAgents(20, Collections.emptyList());
    final List<DiscoveryPeer> agentPeers =
        agents.stream().map(PeerDiscoveryAgent::getAdvertisedPeer).collect(Collectors.toList());

    // Start another bootstrap peer pointing to those 20 agents.
    final PeerDiscoveryAgent agent = startDiscoveryAgent(agentPeers);
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(agent.getPeers()).hasSize(20);
              assertThat(agent.getPeers())
                  .allMatch(p -> p.getStatus() == PeerDiscoveryStatus.BONDED);
            });

    // Send a PING so we can exchange messages with the latter agent.
    final DiscoveryTestSocket testSocket = startTestSocket();
    Packet packet =
        Packet.create(
            PacketType.PING,
            PingPacketData.create(
                testSocket.getPeer().getEndpoint(), testSocket.getPeer().getEndpoint()),
            testSocket.getKeyPair());
    testSocket.sendToAgent(agent, packet);

    // Wait until PONG is received.
    final Packet pong = testSocket.compulsoryPoll();
    assertThat(pong.getType()).isEqualTo(PacketType.PONG);

    // Send a FIND_NEIGHBORS message.
    packet =
        Packet.create(
            PacketType.FIND_NEIGHBORS,
            FindNeighborsPacketData.create(agents.get(0).getAdvertisedPeer().getId()),
            testSocket.getKeyPair());
    testSocket.sendToAgent(agent, packet);

    // Wait until NEIGHBORS is received.
    packet = testSocket.compulsoryPoll();
    assertThat(packet.getType()).isEqualTo(PacketType.NEIGHBORS);

    // Assert that we only received 16 items.
    final NeighborsPacketData neighbors = packet.getPacketData(NeighborsPacketData.class).get();
    assertThat(neighbors).isNotNull();
    assertThat(neighbors.getNodes()).hasSize(16);

    // Assert that after removing those 16 items we're left with either 4 or 5.
    // If we are left with 5, the test peer was returned as an item, assert that this is the case.
    agentPeers.removeAll(neighbors.getNodes());
    assertThat(agentPeers.size()).isBetween(4, 5);
    if (agentPeers.size() == 5) {
      assertThat(neighbors.getNodes()).contains(testSocket.getPeer());
    }
  }

  @Test
  public void shouldEvictPeerOnDisconnect() {
    final Vertx vertx = Vertx.vertx();

    final SECP256K1.KeyPair keyPair1 = SECP256K1.KeyPair.generate();
    final PeerDiscoveryAgent peerDiscoveryAgent1 =
        new PeerDiscoveryAgent(
            vertx,
            keyPair1,
            DiscoveryConfiguration.create().setBindHost("127.0.0.1").setBindPort(0),
            () -> true,
            new PeerBlacklist());
    peerDiscoveryAgent1.start(0).join();
    final DefaultPeer peer = peerDiscoveryAgent1.getAdvertisedPeer();

    final SECP256K1.KeyPair keyPair2 = SECP256K1.KeyPair.generate();
    final PeerDiscoveryAgent peerDiscoveryAgent2 =
        new PeerDiscoveryAgent(
            vertx,
            keyPair2,
            DiscoveryConfiguration.create()
                .setBindHost("127.0.0.1")
                .setBindPort(0)
                .setBootstrapPeers(Lists.newArrayList(peer)),
            () -> true,
            new PeerBlacklist());
    peerDiscoveryAgent2.start(0).join();

    assertThat(peerDiscoveryAgent2.getPeers().size()).isEqualTo(1);

    final PeerConnection peerConnection = createAnonymousPeerConnection(peer.getId());
    peerDiscoveryAgent2.onDisconnect(peerConnection, DisconnectReason.REQUESTED, true);

    assertThat(peerDiscoveryAgent2.getPeers().size()).isEqualTo(0);
  }

  @Test
  public void doesNotBlacklistPeerForNormalDisconnect() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final DiscoveryTestSocket peerSocket = startTestSocket();
    final PeerConnection wirePeer = createAnonymousPeerConnection(peerSocket.getPeer().getId());

    // Bond to peer
    bondViaIncomingPing(agent, peerSocket);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect with innocuous reason
    blacklist.onDisconnect(wirePeer, DisconnectReason.TOO_MANY_PEERS, false);
    agent.onDisconnect(wirePeer, DisconnectReason.TOO_MANY_PEERS, false);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, peerSocket);

    // Check peer was allowed to connect
    assertThat(agent.getPeers()).hasSize(1);
  }

  @Test
  public void blacklistPeerForBadBehavior() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final DiscoveryTestSocket peerSocket = startTestSocket();
    final PeerConnection wirePeer = createAnonymousPeerConnection(peerSocket.getPeer().getId());

    // Bond to peer
    bondViaIncomingPing(agent, peerSocket);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect with problematic reason
    blacklist.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, false);
    agent.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, false);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, peerSocket);

    // Check peer was not allowed to connect
    assertThat(agent.getPeers()).hasSize(0);
  }

  @Test
  public void doesNotBlacklistPeerForOurBadBehavior() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final DiscoveryTestSocket peerSocket = startTestSocket();
    final PeerConnection wirePeer = createAnonymousPeerConnection(peerSocket.getPeer().getId());

    // Bond to peer
    bondViaIncomingPing(agent, peerSocket);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect with problematic reason
    blacklist.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, true);
    agent.onDisconnect(wirePeer, DisconnectReason.BREACH_OF_PROTOCOL, true);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, peerSocket);

    // Check peer was allowed to connect
    assertThat(agent.getPeers()).hasSize(1);
  }

  @Test
  public void blacklistIncompatiblePeer() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final DiscoveryTestSocket peerSocket = startTestSocket();
    final PeerConnection wirePeer = createAnonymousPeerConnection(peerSocket.getPeer().getId());

    // Bond to peer
    bondViaIncomingPing(agent, peerSocket);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect
    blacklist.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, false);
    agent.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, false);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, peerSocket);

    // Check peer was not allowed to connect
    assertThat(agent.getPeers()).hasSize(0);
  }

  @Test
  public void blacklistIncompatiblePeerWhoIssuesDisconnect() throws Exception {
    // Start an agent with no bootstrap peers.
    final PeerBlacklist blacklist = new PeerBlacklist();
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList(), blacklist);
    // Setup peer
    final DiscoveryTestSocket peerSocket = startTestSocket();
    final PeerConnection wirePeer = createAnonymousPeerConnection(peerSocket.getPeer().getId());

    // Bond to peer
    bondViaIncomingPing(agent, peerSocket);
    assertThat(agent.getPeers()).hasSize(1);

    // Disconnect
    blacklist.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, true);
    agent.onDisconnect(wirePeer, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION, true);
    // Confirm peer was removed
    assertThat(agent.getPeers()).hasSize(0);

    // Bond again
    bondViaIncomingPing(agent, peerSocket);

    // Check peer was not allowed to connect
    assertThat(agent.getPeers()).hasSize(0);
  }

  @Test
  public void shouldBeActiveWhenConfigIsTrue() {
    final DiscoveryConfiguration config = new DiscoveryConfiguration();
    config.setActive(true).setBindPort(0);

    final PeerDiscoveryAgent agent = startDiscoveryAgent(config, new PeerBlacklist());

    assertThat(agent.isActive()).isTrue();
  }

  @Test
  public void shouldNotBeActiveWhenConfigIsFalse() {
    final DiscoveryConfiguration config = new DiscoveryConfiguration();
    config.setActive(false).setBindPort(0);

    final PeerDiscoveryAgent agent = startDiscoveryAgent(config, new PeerBlacklist());

    assertThat(agent.isActive()).isFalse();
  }

  private PeerConnection createAnonymousPeerConnection(final BytesValue id) {
    return new PeerConnection() {
      @Override
      public void send(final Capability capability, final MessageData message)
          throws PeerNotConnected {}

      @Override
      public Set<Capability> getAgreedCapabilities() {
        return null;
      }

      @Override
      public PeerInfo getPeer() {
        return new PeerInfo(0, null, null, 0, id);
      }

      @Override
      public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {}

      @Override
      public void disconnect(final DisconnectReason reason) {}

      @Override
      public SocketAddress getLocalAddress() {
        return null;
      }

      @Override
      public SocketAddress getRemoteAddress() {
        return null;
      }
    };
  }
}
