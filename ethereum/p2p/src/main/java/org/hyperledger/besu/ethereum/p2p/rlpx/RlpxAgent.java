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
package org.hyperledger.besu.ethereum.p2p.rlpx;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerPrivileges;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.ConnectionInitializer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnectionEvents;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerRlpxPermissions;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.NettyConnectionInitializer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.NettyTLSConnectionInitializer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.ShouldConnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.util.Subscribers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RlpxAgent {
  private static final Logger LOG = LoggerFactory.getLogger(RlpxAgent.class);

  private final LocalNode localNode;
  private final PeerConnectionEvents connectionEvents;
  private final ConnectionInitializer connectionInitializer;
  private final Subscribers<ConnectCallback> connectSubscribers = Subscribers.create();
  private final List<ShouldConnectCallback> outgoingConnectRequestSubscribers = new ArrayList<>();

  private final PeerRlpxPermissions peerPermissions;
  private final PeerPrivileges peerPrivileges;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Counter connectedPeersCounter;

  private Callable<Stream<PeerConnection>> getAllConnectionsCallback;
  private Callable<Stream<PeerConnection>> getAllActiveConnectionsCallback;

  private RlpxAgent(
      final LocalNode localNode,
      final PeerConnectionEvents connectionEvents,
      final ConnectionInitializer connectionInitializer,
      final PeerRlpxPermissions peerPermissions,
      final PeerPrivileges peerPrivileges,
      final int upperBoundConnections,
      final MetricsSystem metricsSystem) {
    this.localNode = localNode;
    this.connectionEvents = connectionEvents;
    this.connectionInitializer = connectionInitializer;
    this.peerPermissions = peerPermissions;
    this.peerPrivileges = peerPrivileges;

    // Setup metrics
    connectedPeersCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PEERS, "connected_total", "Total number of peers connected");

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.ETHEREUM,
        "peer_limit",
        "The maximum number of peers this node allows to connect",
        () -> upperBoundConnections);
  }

  public static Builder builder() {
    return new Builder();
  }

  public CompletableFuture<Integer> start() {
    if (!started.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Unable to start an already started " + getClass().getSimpleName()));
    }

    setupListeners();
    return connectionInitializer
        .start()
        .thenApply(
            (socketAddress) -> {
              LOG.info("P2P RLPx agent started and listening on {}.", socketAddress);
              return socketAddress.getPort();
            })
        .whenComplete(
            (res, err) -> {
              if (err != null) {
                // the detail of this error is already logged by the completeExceptionally() call in
                // NettyConnectionInitializer
                LOG.error("Failed to start P2P RLPx agent. Check for port conflicts.");
              }
            });
  }

  public CompletableFuture<Void> stop() {
    if (!started.get() || !stopped.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Illegal attempt to stop " + getClass().getSimpleName()));
    }

    streamConnections().forEach((conn) -> conn.disconnect(DisconnectReason.CLIENT_QUITTING));
    return connectionInitializer.stop();
  }

  public Stream<? extends PeerConnection> streamConnections() {
    try {
      return getAllConnectionsCallback.call();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Stream<? extends PeerConnection> streamActiveConnections() {
    try {
      return getAllActiveConnectionsCallback.call();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public int getConnectionCount() {
    try {
      return (int) getAllActiveConnectionsCallback.call().count();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void connect(final Stream<? extends Peer> peerStream) {
    if (!localNode.isReady()) {
      return;
    }
    peerStream.forEach(this::connect);
  }

  private String logConnectionsByIdToString() {
    final String connectionsList;
    try {
      connectionsList =
          getAllActiveConnectionsCallback
              .call()
              .map(PeerConnection::toString)
              .collect(Collectors.joining(",\n"));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return getConnectionCount() + " ConnectionsById {\n" + connectionsList + "}";
  }

  public void disconnect(final Bytes peerId, final DisconnectReason reason) {
    try {
      getAllActiveConnectionsCallback
          .call()
          .filter(c -> c.getPeer().getId().equals(peerId))
          .forEach(c -> c.disconnect(reason));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Connect to the peer
   *
   * @param peer The peer to connect to
   * @return A future that will resolve to the existing or newly-established connection with this
   *     peer.
   */
  public CompletableFuture<PeerConnection> connect(final Peer peer) {
    // Check if we're ready to establish connections
    if (!localNode.isReady()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Cannot connect before "
                  + this.getClass().getSimpleName()
                  + " has finished starting"));
    }
    // Check peer is valid
    final EnodeURL enode = peer.getEnodeURL();
    if (!enode.isListening()) {
      final String errorMsg = "Attempt to connect to peer with no listening port: " + enode;
      LOG.warn(errorMsg);
      return CompletableFuture.failedFuture((new IllegalArgumentException(errorMsg)));
    }

    // Check permissions
    if (!peerPermissions.allowNewOutboundConnectionTo(peer)) {
      return CompletableFuture.failedFuture(peerPermissions.newOutboundConnectionException(peer));
    }

    final CompletableFuture<PeerConnection> peerConnectionCompletableFuture;
    if (checkWhetherToConnect(peer)) {
      peerConnectionCompletableFuture = initiateOutboundConnection(peer);
      peerConnectionCompletableFuture.whenComplete(
          (peerConnection, throwable) -> {
            if (throwable == null) {
              dispatchConnect(peerConnection);
            }
          });
    } else {
      final String errorMsg =
          "None of the ProtocolManagers want to connect to peer " + peer.getId();
      LOG.debug(errorMsg);
      return CompletableFuture.failedFuture((new RuntimeException(errorMsg)));
    }
    return peerConnectionCompletableFuture;
  }

  private boolean checkWhetherToConnect(final Peer peer) {
    return outgoingConnectRequestSubscribers.stream()
        .anyMatch(callback -> callback.shouldConnect(peer));
  }

  private void setupListeners() {
    connectionInitializer.subscribeIncomingConnect(this::handleIncomingConnection);
    connectionEvents.subscribeDisconnect(this::handleDisconnect);
    peerPermissions.subscribeUpdate(this::handlePermissionsUpdate);
  }

  private void handleDisconnect(
      final PeerConnection peerConnection,
      final DisconnectReason disconnectReason,
      final boolean initiatedByPeer) {
    traceLambda(LOG, "{}", this::logConnectionsByIdToString);
  }

  private void handlePermissionsUpdate(
      final boolean permissionsRestricted, final Optional<List<Peer>> peers) {
    if (!permissionsRestricted) {
      // Nothing to do
      return;
    }

    final Stream<? extends PeerConnection> connectionsToCheck;
    if (peers.isPresent()) {
      final List<Bytes> changedPeersIds =
          peers.get().stream().map(p -> p.getId()).collect(Collectors.toList());
      connectionsToCheck =
          streamConnections().filter(c -> changedPeersIds.contains(c.getPeer().getId()));
    } else {
      connectionsToCheck = streamConnections();
    }

    connectionsToCheck.forEach(
        connection -> {
          if (!peerPermissions.allowOngoingConnection(
              connection.getPeer(), connection.inboundInitiated())) {
            LOG.debug(
                "Disconnecting from peer that is not permitted to maintain ongoing connection: {}",
                connection);
            connection.disconnect(DisconnectReason.REQUESTED);
          }
        });
  }

  private CompletableFuture<PeerConnection> initiateOutboundConnection(final Peer peer) {
    LOG.trace("Initiating connection to peer: {}", peer.getEnodeURL());
    if (peer instanceof DiscoveryPeer) {
      ((DiscoveryPeer) peer).setLastAttemptedConnection(System.currentTimeMillis());
    }

    return connectionInitializer
        .connect(peer)
        .whenComplete(
            (conn, err) -> {
              if (err != null) {
                LOG.debug("Failed to connect to peer {}: {}", peer.getId(), err);
              } else {
                LOG.debug("Outbound connection established to peer: {}", peer.getId());
              }
            });
  }

  public boolean canExceedConnectionLimits(final Peer peer) {
    return peerPrivileges.canExceedConnectionLimits(peer);
  }

  public void setGetAllConnectionsCallback(final Callable<Stream<PeerConnection>> callback) {
    this.getAllConnectionsCallback = callback;
  }

  public void setGetAllActiveConnectionsCallback(final Callable<Stream<PeerConnection>> callback) {
    this.getAllActiveConnectionsCallback = callback;
  }

  private void handleIncomingConnection(final PeerConnection peerConnection) {
    // TODO: when we get here, we are already connected and have spent the time and effort to go
    // through the handshake. We might want to put in a check before doing the handshake if we want
    // to connect to that peer at that time
    final Peer peer = peerConnection.getPeer();
    // Deny connection if our local node isn't ready
    if (!localNode.isReady()) {
      LOG.debug("Node is not ready. Disconnect incoming connection: {}", peerConnection);
      peerConnection.disconnect(DisconnectReason.UNKNOWN);
      return;
    }

    // Disconnect if not permitted
    if (!peerPermissions.allowNewInboundConnectionFrom(peer)) {
      LOG.debug(
          "Node is not permitted to connect. Disconnect incoming connection: {}", peerConnection);
      peerConnection.disconnect(DisconnectReason.UNKNOWN);
      return;
    }

    dispatchConnect(peerConnection);
  }

  public void subscribeMessage(final Capability capability, final MessageCallback callback) {
    connectionEvents.subscribeMessage(capability, callback);
  }

  public void subscribeConnect(final ConnectCallback callback) {
    connectSubscribers.subscribe(callback);
  }

  public void subscribeOutgoingConnectRequest(final ShouldConnectCallback callback) {
    outgoingConnectRequestSubscribers.add(callback);
  }

  public void subscribeDisconnect(final DisconnectCallback callback) {
    connectionEvents.subscribeDisconnect(callback);
  }

  private void dispatchConnect(final PeerConnection connection) {
    connectedPeersCounter.inc();
    connectSubscribers.forEach(c -> c.onConnect(connection));
  }

  public static class Builder {
    private NodeKey nodeKey;
    private LocalNode localNode;
    private RlpxConfiguration config;
    private PeerPrivileges peerPrivileges;
    private PeerPermissions peerPermissions;
    private ConnectionInitializer connectionInitializer;
    private PeerConnectionEvents connectionEvents;
    private MetricsSystem metricsSystem;
    private Optional<TLSConfiguration> p2pTLSConfiguration;

    private Builder() {}

    public RlpxAgent build() {
      validate();

      if (connectionEvents == null) {
        connectionEvents = new PeerConnectionEvents(metricsSystem);
      }
      if (connectionInitializer == null) {
        if (p2pTLSConfiguration.isPresent()) {
          LOG.debug("TLS Configuration found using NettyTLSConnectionInitializer");
          connectionInitializer =
              new NettyTLSConnectionInitializer(
                  nodeKey,
                  config,
                  localNode,
                  connectionEvents,
                  metricsSystem,
                  p2pTLSConfiguration.get());
        } else {
          LOG.debug("Using default NettyConnectionInitializer");
          connectionInitializer =
              new NettyConnectionInitializer(
                  nodeKey, config, localNode, connectionEvents, metricsSystem);
        }
      }

      final PeerRlpxPermissions rlpxPermissions =
          new PeerRlpxPermissions(localNode, peerPermissions);
      return new RlpxAgent(
          localNode,
          connectionEvents,
          connectionInitializer,
          rlpxPermissions,
          peerPrivileges,
          config.getPeerUpperBound(),
          metricsSystem);
    }

    private void validate() {
      checkState(nodeKey != null, "NodeKey must be configured");
      checkState(localNode != null, "LocalNode must be configured");
      checkState(config != null, "RlpxConfiguration must be set");
      checkState(peerPrivileges != null, "PeerPrivileges must be configured");
      checkState(peerPermissions != null, "PeerPermissions must be configured");
      checkState(metricsSystem != null, "MetricsSystem must be configured");
    }

    public Builder nodeKey(final NodeKey nodeKey) {
      checkNotNull(nodeKey);
      this.nodeKey = nodeKey;
      return this;
    }

    public Builder localNode(final LocalNode localNode) {
      checkNotNull(localNode);
      this.localNode = localNode;
      return this;
    }

    public Builder connectionInitializer(final ConnectionInitializer connectionInitializer) {
      checkNotNull(connectionInitializer);
      this.connectionInitializer = connectionInitializer;
      return this;
    }

    public Builder config(final RlpxConfiguration config) {
      checkNotNull(config);
      this.config = config;
      return this;
    }

    public Builder peerPrivileges(final PeerPrivileges peerPrivileges) {
      checkNotNull(peerPrivileges);
      this.peerPrivileges = peerPrivileges;
      return this;
    }

    public Builder peerPermissions(final PeerPermissions peerPermissions) {
      checkNotNull(peerPermissions);
      this.peerPermissions = peerPermissions;
      return this;
    }

    public Builder connectionEvents(final PeerConnectionEvents connectionEvents) {
      checkNotNull(connectionEvents);
      this.connectionEvents = connectionEvents;
      return this;
    }

    public Builder metricsSystem(final MetricsSystem metricsSystem) {
      checkNotNull(metricsSystem);
      this.metricsSystem = metricsSystem;
      return this;
    }

    public Builder p2pTLSConfiguration(final Optional<TLSConfiguration> p2pTLSConfiguration) {
      this.p2pTLSConfiguration = p2pTLSConfiguration;
      return this;
    }
  }
}
