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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.forkid.ForkId;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PacketType;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PingPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PongPacketData;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class PeerDiscoveryTestHelper {
  private static final String LOOPBACK_IP_ADDR = "127.0.0.1";

  private final AtomicInteger nextAvailablePort = new AtomicInteger(1);
  Map<Bytes, MockPeerDiscoveryAgent> agents = new HashMap<>();

  public static List<NodeKey> generateNodeKeys(final int count) {
    return Stream.generate(NodeKeyUtils::generate).limit(count).collect(Collectors.toList());
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

  public List<DiscoveryPeer> createDiscoveryPeers(final List<NodeKey> nodeKeys) {
    return nodeKeys.stream().map(this::createDiscoveryPeer).collect(Collectors.toList());
  }

  public DiscoveryPeer createDiscoveryPeer() {
    return createDiscoveryPeer(NodeKeyUtils.generate());
  }

  public DiscoveryPeer createDiscoveryPeer(final NodeKey nodeKey) {
    final Bytes peerId = nodeKey.getPublicKey().getEncodedBytes();
    final int port = nextAvailablePort.incrementAndGet();
    final DiscoveryPeer discoveryPeer =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(peerId)
                .ipAddress(LOOPBACK_IP_ADDR)
                .discoveryAndListeningPorts(port)
                .build());
    discoveryPeer.setNodeRecord(
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList()));

    return discoveryPeer;
  }

  public Packet createPingPacket(
      final MockPeerDiscoveryAgent fromAgent, final MockPeerDiscoveryAgent toAgent) {
    return Packet.create(
        PacketType.PING,
        PingPacketData.create(
            Optional.of(fromAgent.getAdvertisedPeer().get().getEndpoint()),
            toAgent.getAdvertisedPeer().get().getEndpoint(),
            UInt64.ONE),
        fromAgent.getNodeKey());
  }

  public Packet createPongPacket(final MockPeerDiscoveryAgent toAgent, final Hash pingHash) {
    return Packet.create(
        PacketType.PONG,
        PongPacketData.create(
            toAgent.getAdvertisedPeer().get().getEndpoint(), pingHash, UInt64.ONE),
        toAgent.getNodeKey());
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
   * Starts multiple discovery agents with the provided bootstrap peers.
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
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers);

    return startDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent startDiscoveryAgent(final DiscoveryPeer... bootstrapPeers) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers);

    return startDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent startDiscoveryAgent(
      final String advertisedHost, final DiscoveryPeer... bootstrapPeers) {
    final AgentBuilder agentBuilder =
        agentBuilder().bootstrapPeers(bootstrapPeers).advertisedHost(advertisedHost);

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
      final List<DiscoveryPeer> bootstrapPeers, final PeerPermissions peerPermissions) {
    final AgentBuilder agentBuilder =
        agentBuilder().bootstrapPeers(bootstrapPeers).peerPermissions(peerPermissions);

    return startDiscoveryAgent(agentBuilder);
  }

  public MockPeerDiscoveryAgent startDiscoveryAgent(final AgentBuilder agentBuilder) {
    final MockPeerDiscoveryAgent agent = createDiscoveryAgent(agentBuilder);
    agent.start(nextAvailablePort.incrementAndGet()).join();
    return agent;
  }

  public MockPeerDiscoveryAgent createDiscoveryAgent(final List<DiscoveryPeer> bootstrapPeers) {
    final AgentBuilder agentBuilder = agentBuilder().bootstrapPeers(bootstrapPeers);

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

    private final Map<Bytes, MockPeerDiscoveryAgent> agents;
    private final AtomicInteger nextAvailablePort;

    private List<EnodeURL> bootnodes = Collections.emptyList();
    private boolean enabled = true;
    private PeerPermissions peerPermissions = PeerPermissions.noop();
    private String advertisedHost = "127.0.0.1";
    private OptionalInt bindPort = OptionalInt.empty();
    private NodeKey nodeKey = NodeKeyUtils.generate();
    private NodeRecord nodeRecord =
        NodeRecord.fromValues(IdentitySchemaInterpreter.V4, UInt64.ONE, Collections.emptyList());

    private AgentBuilder(
        final Map<Bytes, MockPeerDiscoveryAgent> agents, final AtomicInteger nextAvailablePort) {
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
      this.enabled = active;
      return this;
    }

    public AgentBuilder advertisedHost(final String host) {
      checkNotNull(host);
      this.advertisedHost = host;
      return this;
    }

    public AgentBuilder bindPort(final int bindPort) {
      if (bindPort == 0) {
        // Zero means pick the next available port
        this.bindPort = OptionalInt.empty();
        return this;
      }
      this.bindPort = OptionalInt.of(bindPort);
      return this;
    }

    public AgentBuilder nodeKey(final NodeKey nodeKey) {
      checkNotNull(nodeKey);
      this.nodeKey = nodeKey;
      return this;
    }

    public AgentBuilder nodeRecord(final NodeRecord nodeRecord) {
      this.nodeRecord = nodeRecord;
      return this;
    }

    public MockPeerDiscoveryAgent build() {
      final int port = bindPort.orElseGet(nextAvailablePort::incrementAndGet);
      final DiscoveryConfiguration config = new DiscoveryConfiguration();
      final NatService natService = new NatService(Optional.empty());
      config.setBootnodes(bootnodes);
      config.setAdvertisedHost(advertisedHost);
      config.setBindPort(port);
      config.setEnabled(enabled);
      config.setFilterOnEnrForkId(false);

      final ForkIdManager mockForkIdManager = mock(ForkIdManager.class);
      final ForkId forkId = new ForkId(Bytes.EMPTY, Bytes.EMPTY);
      when(mockForkIdManager.getForkIdForChainHead()).thenReturn(forkId);
      when(mockForkIdManager.peerCheck(forkId)).thenReturn(true);
      final RlpxAgent rlpxAgent = mock(RlpxAgent.class);
      when(rlpxAgent.connect(any()))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
      final MockPeerDiscoveryAgent mockPeerDiscoveryAgent =
          new MockPeerDiscoveryAgent(
              nodeKey, config, peerPermissions, agents, natService, mockForkIdManager, rlpxAgent);
      mockPeerDiscoveryAgent.getAdvertisedPeer().ifPresent(peer -> peer.setNodeRecord(nodeRecord));

      return mockPeerDiscoveryAgent;
    }
  }
}
