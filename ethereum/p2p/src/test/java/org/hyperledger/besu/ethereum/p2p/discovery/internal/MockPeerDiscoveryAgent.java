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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static org.apache.tuweni.bytes.Bytes.wrapBuffer;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController.AsyncExecutor;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.nat.NatService;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockPeerDiscoveryAgent extends PeerDiscoveryAgent {
  private static final Logger LOG = LoggerFactory.getLogger(MockPeerDiscoveryAgent.class);

  // The set of known agents operating on the network
  private final Map<Bytes, MockPeerDiscoveryAgent> agentNetwork;
  private final Deque<IncomingPacket> incomingPackets = new ArrayDeque<>();
  private boolean isRunning = false;

  public MockPeerDiscoveryAgent(
      final NodeKey nodeKey,
      final DiscoveryConfiguration config,
      final PeerPermissions peerPermissions,
      final Map<Bytes, MockPeerDiscoveryAgent> agentNetwork,
      final NatService natService,
      final Supplier<List<Bytes>> forkIdSupplier) {
    super(
        nodeKey,
        config,
        peerPermissions,
        natService,
        new NoOpMetricsSystem(),
        new InMemoryKeyValueStorageProvider(),
        forkIdSupplier);
    this.agentNetwork = agentNetwork;
  }

  public void processIncomingPacket(final MockPeerDiscoveryAgent fromAgent, final Packet packet) {
    // Cycle packet through encode / decode to make clone of any data
    // This ensures that any data passed between agents is not shared
    final Packet packetClone = Packet.decode(packet.encode());
    incomingPackets.add(new IncomingPacket(fromAgent, packetClone));
    handleIncomingPacket(fromAgent.getAdvertisedPeer().get().getEndpoint(), packetClone);
  }

  /**
   * Get and clear the list of any incoming packets to this agent.
   *
   * @return A list of packets received by this agent
   */
  public List<IncomingPacket> getIncomingPackets() {
    List<IncomingPacket> packets = Arrays.asList(incomingPackets.toArray(new IncomingPacket[0]));
    incomingPackets.clear();
    return packets;
  }

  @Override
  protected CompletableFuture<InetSocketAddress> listenForConnections() {
    isRunning = true;
    // Skip network setup for tests
    InetSocketAddress address = new InetSocketAddress(config.getBindHost(), config.getBindPort());
    return CompletableFuture.completedFuture(address);
  }

  @Override
  protected CompletableFuture<Void> sendOutgoingPacket(
      final DiscoveryPeer toPeer, final Packet packet) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    if (!this.isRunning) {
      result.completeExceptionally(new Exception("Attempt to send message from an inactive agent"));
    }

    MockPeerDiscoveryAgent toAgent = agentNetwork.get(toPeer.getId());
    if (toAgent == null) {
      result.completeExceptionally(
          new Exception(
              "Attempt to send to unknown peer.  Agents must be constructed through PeerDiscoveryTestHelper."));
      return result;
    }

    final DiscoveryPeer agentPeer = toAgent.getAdvertisedPeer().get();
    if (!toPeer.getEndpoint().getHost().equals(agentPeer.getEndpoint().getHost())) {
      LOG.warn(
          "Attempt to send packet to discovery peer using the wrong host address.  Sending to {}, but discovery peer is listening on {}",
          toPeer.getEndpoint().getHost(),
          agentPeer.getEndpoint().getHost());
    } else if (toPeer.getEndpoint().getUdpPort() != agentPeer.getEndpoint().getUdpPort()) {
      LOG.warn(
          "Attempt to send packet to discovery peer using the wrong udp port.  Sending to {}, but discovery peer is listening on {}",
          toPeer.getEndpoint().getUdpPort(),
          agentPeer.getEndpoint().getUdpPort());
    } else if (!toAgent.isRunning) {
      LOG.warn("Attempt to send packet to an inactive peer.");
    } else {
      toAgent.processIncomingPacket(this, packet);
    }
    result.complete(null);
    return result;
  }

  @Override
  protected TimerUtil createTimer() {
    return new MockTimerUtil();
  }

  @Override
  protected AsyncExecutor createWorkerExecutor() {
    return new BlockingAsyncExecutor();
  }

  @Override
  public CompletableFuture<?> stop() {
    isRunning = false;
    return CompletableFuture.completedFuture(null);
  }

  @Override
  protected void handleOutgoingPacketError(
      final Throwable err, final DiscoveryPeer peer, final Packet packet) {
    LOG.warn(
        "Sending to peer {} failed, packet: {}, stacktrace: {}",
        peer,
        wrapBuffer(packet.encode()),
        err);
  }

  public NodeKey getNodeKey() {
    return nodeKey;
  }

  public static class IncomingPacket {
    public final MockPeerDiscoveryAgent fromAgent;
    public final Packet packet;

    public IncomingPacket(final MockPeerDiscoveryAgent fromAgent, final Packet packet) {
      this.fromAgent = fromAgent;
      this.packet = packet;
    }
  }
}
