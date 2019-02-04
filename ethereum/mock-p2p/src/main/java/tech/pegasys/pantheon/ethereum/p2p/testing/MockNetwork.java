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
package tech.pegasys.pantheon.ethereum.p2p.testing;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.Subscribers;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Mock network implementation that allows passing {@link MessageData} between arbitrary peers. This
 * completely bypasses the TCP layer by directly passing {@link MessageData} from {@link
 * MockNetwork.MockPeerConnection#send(Capability, MessageData)} to callbacks registered on {@link
 * MockNetwork.MockP2PNetwork}s.
 */
public final class MockNetwork {

  private final Map<Peer, MockNetwork.MockP2PNetwork> nodes = new HashMap<>();
  private final List<Capability> capabilities;

  public MockNetwork(final List<Capability> capabilities) {
    this.capabilities = capabilities;
  }

  /**
   * Get the {@link P2PNetwork} that assumes a given {@link Peer} as the local node. This does not
   * connect {@link Peer} to any other peer. Any connections established by {@link #connect(Peer,
   * Peer)} require that both participating {@link Peer} have previously been passed to this method.
   *
   * @param peer Peer to get {@link P2PNetwork} for
   * @return P2PNetwork as seen by {@link Peer}
   */
  public P2PNetwork setup(final Peer peer) {
    synchronized (this) {
      return nodes.computeIfAbsent(peer, p -> new MockNetwork.MockP2PNetwork(peer, this));
    }
  }

  private PeerConnection connect(final Peer source, final Peer target) {
    synchronized (this) {
      final MockNetwork.MockPeerConnection establishedConnection =
          new MockNetwork.MockPeerConnection(source, target, this);
      final MockP2PNetwork sourceNode = nodes.get(source);
      final MockP2PNetwork targetNode = nodes.get(target);
      sourceNode.connections.put(target, establishedConnection);
      final MockNetwork.MockPeerConnection backChannel =
          new MockNetwork.MockPeerConnection(target, source, this);
      targetNode.connections.put(source, backChannel);
      sourceNode.connectCallbacks.forEach(c -> c.accept(establishedConnection));
      targetNode.connectCallbacks.forEach(c -> c.accept(backChannel));
      return establishedConnection;
    }
  }

  private void disconnect(
      final MockNetwork.MockPeerConnection connection, final DisconnectReason reason) {
    synchronized (this) {
      final MockP2PNetwork sourceNode = nodes.get(connection.from);
      final MockP2PNetwork targetNode = nodes.get(connection.to);
      if (targetNode.connections.remove(connection.from) == null
          || sourceNode.connections.remove(connection.to) == null) {
        throw new IllegalStateException(
            String.format("No connection between %s and %s", connection.from, connection.to));
      }
      targetNode.disconnectCallbacks.forEach(c -> c.onDisconnect(connection, reason, true));
      sourceNode.disconnectCallbacks.forEach(
          c -> c.onDisconnect(connection, DisconnectReason.REQUESTED, false));
    }
  }

  private final class MockP2PNetwork implements P2PNetwork {

    private final MockNetwork network;

    private final Map<Peer, MockNetwork.MockPeerConnection> connections = new HashMap<>();

    private final Peer self;

    private final Map<Capability, Subscribers<Consumer<Message>>> protocolCallbacks =
        new ConcurrentHashMap<>();

    private final Subscribers<Consumer<PeerConnection>> connectCallbacks = new Subscribers<>();

    private final Subscribers<DisconnectCallback> disconnectCallbacks = new Subscribers<>();

    MockP2PNetwork(final Peer self, final MockNetwork network) {
      this.self = self;
      this.network = network;
    }

    @Override
    public Collection<PeerConnection> getPeers() {
      synchronized (network) {
        return new ArrayList<>(connections.values());
      }
    }

    @Override
    public CompletableFuture<PeerConnection> connect(final Peer peer) {
      synchronized (network) {
        if (network.nodes.containsKey(peer)) {
          final PeerConnection connection = connections.get(peer);
          if (connection == null) {
            return CompletableFuture.completedFuture(network.connect(self, peer));
          } else {
            return CompletableFuture.completedFuture(connection);
          }
        } else {
          return CompletableFuture.supplyAsync(
              () -> {
                throw new IllegalStateException(
                    String.format("Tried to connect to unknown peer %s", peer));
              });
        }
      }
    }

    @Override
    public void subscribe(final Capability capability, final Consumer<Message> callback) {
      protocolCallbacks.computeIfAbsent(capability, key -> new Subscribers<>()).subscribe(callback);
    }

    @Override
    public void subscribeConnect(final Consumer<PeerConnection> callback) {
      connectCallbacks.subscribe(callback);
    }

    @Override
    public void subscribeDisconnect(final DisconnectCallback callback) {
      disconnectCallbacks.subscribe(callback);
    }

    @Override
    public boolean addMaintainConnectionPeer(final Peer peer) {
      return true;
    }

    @Override
    public void checkMaintainedConnectionPeers() {}

    @Override
    public void stop() {}

    @Override
    public void awaitStop() {}

    @Override
    public InetSocketAddress getDiscoverySocketAddress() {
      return null;
    }

    @Override
    public void run() {}

    @Override
    public void close() {}

    @Override
    public PeerInfo getLocalPeerInfo() {
      return new PeerInfo(
          5, self.getId().toString(), new ArrayList<>(capabilities), 0, self.getId());
    }

    @Override
    public boolean isListening() {
      return true;
    }

    @Override
    public boolean isP2pEnabled() {
      return true;
    }

    @Override
    public Optional<NodeWhitelistController> getNodeWhitelistController() {
      return Optional.empty();
    }
  }

  /**
   * A mock connection between two peers that simply invokes the callbacks on the other side's
   * {@link MockNetwork.MockP2PNetwork}.
   */
  private final class MockPeerConnection implements PeerConnection {

    /** {@link Peer} that this connection originates from. */
    private final Peer from;

    /**
     * Peer that this connection targets and that will receive {@link Message}s sent via {@link
     * #send(Capability, MessageData)}.
     */
    private final Peer to;

    private final MockNetwork network;

    MockPeerConnection(final Peer source, final Peer target, final MockNetwork network) {
      from = source;
      to = target;
      this.network = network;
    }

    @Override
    public void send(final Capability capability, final MessageData message)
        throws PeerNotConnected {
      synchronized (network) {
        final MockNetwork.MockP2PNetwork target = network.nodes.get(to);
        final MockNetwork.MockPeerConnection backChannel = target.connections.get(from);
        if (backChannel != null) {
          final Message msg = new DefaultMessage(backChannel, message);
          final Subscribers<Consumer<Message>> callbacks = target.protocolCallbacks.get(capability);
          if (callbacks != null) {
            callbacks.forEach(c -> c.accept(msg));
          }
        } else {
          throw new PeerNotConnected(String.format("%s not connected to %s", to, from));
        }
      }
    }

    @Override
    public Set<Capability> getAgreedCapabilities() {
      return new HashSet<>(capabilities);
    }

    @Override
    public PeerInfo getPeer() {
      return new PeerInfo(
          5,
          "mock-network-client",
          capabilities,
          to.getEndpoint().getTcpPort().getAsInt(),
          to.getId());
    }

    @Override
    public void terminateConnection(final DisconnectReason reason, final boolean peerInitiated) {
      network.disconnect(this, reason);
    }

    @Override
    public void disconnect(final DisconnectReason reason) {
      network.disconnect(this, reason);
    }

    @Override
    public SocketAddress getLocalAddress() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getRemoteAddress() {
      throw new UnsupportedOperationException();
    }
  }
}
