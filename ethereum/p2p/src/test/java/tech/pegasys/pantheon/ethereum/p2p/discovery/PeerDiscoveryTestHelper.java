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

import static java.util.Arrays.asList;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.Packet;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PacketType;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PingPacketData;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PeerDiscoveryTestHelper {
  private static final String LOOPBACK_IP_ADDR = "127.0.0.1";

  private final AtomicInteger nextAvailablePort = new AtomicInteger(1);
  Map<BytesValue, MockPeerDiscoveryAgent> agents = new HashMap<>();

  public static List<SECP256K1.KeyPair> generateKeyPairs(final int count) {
    return Stream.generate(SECP256K1.KeyPair::generate).limit(count).collect(Collectors.toList());
  }

  /**
   * Starts multiple discovery agents from generated peers.
   *
   * @param count the number of agents to start
   * @return a list of discovery agents.
   */
  public List<DiscoveryPeer> createDiscoveryPeers(final int count) {
    return Stream.generate(this::createDiscoveryPeer).limit(count).collect(Collectors.toList());
  }

  public List<DiscoveryPeer> createDiscoveryPeers(final List<KeyPair> keyPairs) {
    return keyPairs.stream().map(this::createDiscoveryPeer).collect(Collectors.toList());
  }

  public DiscoveryPeer createDiscoveryPeer() {
    return createDiscoveryPeer(KeyPair.generate());
  }

  public DiscoveryPeer createDiscoveryPeer(final KeyPair keyPair) {
    final BytesValue peerId = keyPair.getPublicKey().getEncodedBytes();
    final int port = nextAvailablePort.incrementAndGet();
    return DiscoveryPeer.fromEnode(
        EnodeURL.builder()
            .nodeId(peerId)
            .ipAddress(LOOPBACK_IP_ADDR)
            .discoveryAndListeningPorts(port)
            .build());
  }

  public Packet createPingPacket(
      final MockPeerDiscoveryAgent fromAgent, final MockPeerDiscoveryAgent toAgent) {
    return Packet.create(
        PacketType.PING,
        PingPacketData.create(
            fromAgent.getAdvertisedPeer().get().getEndpoint(),
            toAgent.getAdvertisedPeer().get().getEndpoint(),
            TestClock.fixed()),
        fromAgent.getKeyPair());
  }

  public AgentBuilder agentBuilder() {
    return new AgentBuilder(agents, nextAvailablePort);
  }

  public void sendMessageBetweenAgents(
      final MockPeerDiscoveryAgent fromAgent,
      final MockPeerDiscoveryAgent toAgent,
      final Packet packet) {
    toAgent.processIncomingPacket(fromAgent, packet);
  }

  /**
   * Starts multiple discovery agents with the provided boostrap peers.
   *
   * @param count the number of agents to start
   * @param bootstrapPeers the list of bootstrap peers
   * @return a list of discovery agents.
   */
  public List<MockPeerDiscoveryAgent> startDiscoveryAgents(
      final int count, final List<DiscoveryPeer> bootstrapPeers) {
    return Stream.generate(() -> startDiscoveryAgent(bootstrapPeers))
        .limit(count)
        .collect(Collectors.toList());
  }

  public List<MockPeerDiscoveryAgent> startDiscoveryAgents(final int count) {
    return Stream.generate(() -> startDiscoveryAgent(Collections.emptyList()))
        .limit(count)
        .collect(Collectors.toList());
  }

  /**
   * Start a single discovery agent with the provided bootstrap peers.
   *
   * @param bootstrapPeers the list of bootstrap peers
   * @return a list of discovery agents.
   */
  public MockPeerDiscoveryAgent startDiscoveryAgent(final List<DiscoveryPeer> bootstrapPeers) {
    return startDiscoveryAgent(bootstrapPeers, TestClock.fixed());
  }

  /**
   * Start a single discovery agent with the provided bootstrap peers.
   *
   * @param bootstrapPeers the list of bootstrap peers
   * @param clock the clock to sample timestamps from
   * @return a list of discovery agents.
   */
  public MockPeerDiscoveryAgent startDiscoveryAgent(
      final List<DiscoveryPeer> bootstrapPeers, final Clock clock) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers).clock(clock);

    return startDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent startDiscoveryAgent(final DiscoveryPeer... bootstrapPeers) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers);

    return startDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent startDiscoveryAgent(final Clock clock) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(List.of()).clock(clock);

    return startDiscoveryAgent(agentBuilder);
  }

  /**
   * Start a single discovery agent with the provided bootstrap peers.
   *
   * @param bootstrapPeers the list of bootstrap peers
   * @param peerPermissions peer permissions
   * @return a list of discovery agents.
   */
  public MockPeerDiscoveryAgent startDiscoveryAgent(
      final List<DiscoveryPeer> bootstrapPeers,
      final PeerPermissions peerPermissions,
      final Clock clock) {
    final AgentBuilder agentBuilder =
        agentBuilder().bootstrapPeers(bootstrapPeers).peerPermissions(peerPermissions).clock(clock);

    return startDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent startDiscoveryAgent(final AgentBuilder agentBuilder) {
    final MockPeerDiscoveryAgent agent = createDiscoveryAgent(agentBuilder);
    agent.start(nextAvailablePort.incrementAndGet()).join();
    return agent;
  }

  public MockPeerDiscoveryAgent createDiscoveryAgent(
      final List<DiscoveryPeer> bootstrapPeers, final Clock clock) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers).clock(clock);

    return createDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent createDiscoveryAgent(final DiscoveryPeer... bootstrapPeers) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers);

    return createDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent createDiscoveryAgent(final AgentBuilder agentBuilder) {
    final MockPeerDiscoveryAgent agent = agentBuilder.build();
    agents.put(agent.getId(), agent);
    return agent;
  }

  public static class AgentBuilder {
    private final Map<BytesValue, MockPeerDiscoveryAgent> agents;
    private final AtomicInteger nextAvailablePort;

    private List<EnodeURL> bootnodes = Collections.emptyList();
    private boolean active = true;
    private PeerPermissions peerPermissions = PeerPermissions.noop();
    private Clock clock = TestClock.fixed();

    private AgentBuilder(
        final Map<BytesValue, MockPeerDiscoveryAgent> agents,
        final AtomicInteger nextAvailablePort) {
      this.agents = agents;
      this.nextAvailablePort = nextAvailablePort;
    }

    public AgentBuilder bootstrapPeers(final List<DiscoveryPeer> peers) {
      this.bootnodes = asEnodes(peers);
      return this;
    }

    public AgentBuilder bootstrapPeers(final DiscoveryPeer... peers) {
      return bootstrapPeers(asList(peers));
    }

    public AgentBuilder bootnodes(final EnodeURL... bootnodes) {
      this.bootnodes = Arrays.asList(bootnodes);
      return this;
    }

    private List<EnodeURL> asEnodes(final List<DiscoveryPeer> peers) {
      return peers.stream().map(Peer::getEnodeURL).collect(Collectors.toList());
    }

    public AgentBuilder peerPermissions(final PeerPermissions peerPermissions) {
      this.peerPermissions = peerPermissions;
      return this;
    }

    public AgentBuilder active(final boolean active) {
      this.active = active;
      return this;
    }

    public AgentBuilder clock(final Clock clock) {
      this.clock = clock;
      return this;
    }

    public MockPeerDiscoveryAgent build() {
      final DiscoveryConfiguration config = new DiscoveryConfiguration();
      config.setBootnodes(bootnodes);
      config.setBindPort(nextAvailablePort.incrementAndGet());
      config.setActive(active);

      return new MockPeerDiscoveryAgent(
          SECP256K1.KeyPair.generate(), config, peerPermissions, agents, clock);
    }
  }
}
