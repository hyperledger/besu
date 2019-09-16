/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController.AsyncExecutor;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MockPeerDiscoveryAgent extends PeerDiscoveryAgent {
  // The set of known agents operating on the network
  private final Map<BytesValue, MockPeerDiscoveryAgent> agentNetwork;
  private final Deque<IncomingPacket> incomingPackets = new ArrayDeque<>();

  public MockPeerDiscoveryAgent(
      final KeyPair keyPair,
      final DiscoveryConfiguration config,
      final PeerPermissions peerPermissions,
      final Map<BytesValue, MockPeerDiscoveryAgent> agentNetwork) {
    super(keyPair, config, peerPermissions, Optional.empty(), new NoOpMetricsSystem());
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
    // Skip network setup for tests
    InetSocketAddress address =
        new InetSocketAddress(config.getAdvertisedHost(), config.getBindPort());
    return CompletableFuture.completedFuture(address);
  }

  @Override
  protected CompletableFuture<Void> sendOutgoingPacket(
      final DiscoveryPeer toPeer, final Packet packet) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    MockPeerDiscoveryAgent toAgent = agentNetwork.get(toPeer.getId());
    if (toAgent == null) {
      result.completeExceptionally(
          new Exception(
              "Attempt to send to unknown peer.  Agents must be constructed through PeerDiscoveryTestHelper."));
    } else {
      toAgent.processIncomingPacket(this, packet);
      result.complete(null);
    }
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
    return CompletableFuture.completedFuture(null);
  }

  public KeyPair getKeyPair() {
    return keyPair;
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
