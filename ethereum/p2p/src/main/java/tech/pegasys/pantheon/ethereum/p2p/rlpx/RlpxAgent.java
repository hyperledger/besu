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
package tech.pegasys.pantheon.ethereum.p2p.rlpx;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.ethereum.p2p.peers.LocalNode;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerPrivileges;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.ConnectionInitializer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnectionEvents;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerRlpxPermissions;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.RlpxConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.netty.NettyConnectionInitializer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.util.FutureUtils;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RlpxAgent {
  private static final Logger LOG = LogManager.getLogger();

  private final LocalNode localNode;
  private final PeerConnectionEvents connectionEvents;
  private final ConnectionInitializer connectionInitializer;
  private final Subscribers<ConnectCallback> connectSubscribers = Subscribers.create();

  private final PeerRlpxPermissions peerPermissions;
  private final PeerPrivileges peerPrivileges;
  private final int maxConnections;
  private final int maxRemotelyInitiatedConnections;

  @VisibleForTesting
  final Map<BytesValue, RlpxConnection> connectionsById = new ConcurrentHashMap<>();

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  private final Counter connectedPeersCounter;

  private RlpxAgent(
      final LocalNode localNode,
      final PeerConnectionEvents connectionEvents,
      final ConnectionInitializer connectionInitializer,
      final PeerRlpxPermissions peerPermissions,
      final PeerPrivileges peerPrivileges,
      final int maxConnections,
      final int maxRemotelyInitiatedConnections,
      final MetricsSystem metricsSystem) {
    this.localNode = localNode;
    this.connectionEvents = connectionEvents;
    this.connectionInitializer = connectionInitializer;
    this.peerPermissions = peerPermissions;
    this.peerPrivileges = peerPrivileges;
    this.maxConnections = maxConnections;
    this.maxRemotelyInitiatedConnections =
        Math.min(maxConnections, maxRemotelyInitiatedConnections);

    // Setup metrics
    connectedPeersCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.PEERS, "connected_total", "Total number of peers connected");

    metricsSystem.createIntegerGauge(
        PantheonMetricCategory.ETHEREUM,
        "peer_count",
        "The current number of peers connected",
        this::getConnectionCount);
    metricsSystem.createIntegerGauge(
        PantheonMetricCategory.ETHEREUM,
        "peer_limit",
        "The maximum number of peers this node allows to connect",
        () -> maxConnections);
  }

  public static Builder builder() {
    return new Builder();
  }

  public CompletableFuture<Integer> start() {
    if (!started.compareAndSet(false, true)) {
      return FutureUtils.completedExceptionally(
          new IllegalStateException(
              "Unable to start an already started " + getClass().getSimpleName()));
    }

    setupListeners();
    return connectionInitializer.start();
  }

  public CompletableFuture<Void> stop() {
    if (!started.get() || !stopped.compareAndSet(false, true)) {
      return FutureUtils.completedExceptionally(
          new IllegalStateException("Illegal attempt to stop " + getClass().getSimpleName()));
    }

    streamConnections().forEach((conn) -> conn.disconnect(DisconnectReason.CLIENT_QUITTING));
    return connectionInitializer.stop();
  }

  public Stream<? extends PeerConnection> streamConnections() {
    return connectionsById.values().stream()
        .filter(RlpxConnection::isActive)
        .map(RlpxConnection::getPeerConnection);
  }

  public int getConnectionCount() {
    return Math.toIntExact(streamConnections().count());
  }

  public void connect(final Stream<? extends Peer> peerStream) {
    if (!localNode.isReady()) {
      return;
    }
    final int availablePeerSlots = Math.max(0, maxConnections - getConnectionCount());
    peerStream
        .filter(peer -> !connectionsById.containsKey(peer.getId()))
        .filter(peer -> peer.getEnodeURL().isListening())
        .filter(peerPermissions::allowNewOutboundConnectionTo)
        .limit(availablePeerSlots)
        .forEach(this::connect);
  }

  public void disconnect(final BytesValue peerId, final DisconnectReason reason) {
    RlpxConnection connection = connectionsById.remove(peerId);
    if (connection != null) {
      connection.disconnect(reason);
    }
  }

  public Optional<CompletableFuture<PeerConnection>> getPeerConnection(final Peer peer) {
    final RlpxConnection connection = connectionsById.get(peer.getId());
    return Optional.ofNullable(connection)
        .filter(conn -> !conn.isFailedOrDisconnected())
        .map(RlpxConnection::getFuture);
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
      return FutureUtils.completedExceptionally(
          new IllegalStateException(
              "Cannot connect before "
                  + this.getClass().getSimpleName()
                  + " has finished starting"));
    }
    // Check peer is valid
    final EnodeURL enode = peer.getEnodeURL();
    if (!enode.isListening()) {
      final String errorMsg =
          "Attempt to connect to peer with no listening port: " + enode.toString();
      LOG.warn(errorMsg);
      return FutureUtils.completedExceptionally((new IllegalArgumentException(errorMsg)));
    }

    // Shortcut checks if we're already connected
    final Optional<CompletableFuture<PeerConnection>> peerConnection = getPeerConnection(peer);
    if (peerConnection.isPresent()) {
      return peerConnection.get();
    }
    // Check max peers
    if (!peerPrivileges.canExceedConnectionLimits(peer) && getConnectionCount() >= maxConnections) {
      final String errorMsg =
          "Max peer peer connections established ("
              + maxConnections
              + "). Cannot connect to peer: "
              + peer;
      return FutureUtils.completedExceptionally(new IllegalStateException(errorMsg));
    }
    // Check permissions
    if (!peerPermissions.allowNewOutboundConnectionTo(peer)) {
      return FutureUtils.completedExceptionally(
          peerPermissions.newOutboundConnectionException(peer));
    }

    // Initiate connection or return existing connection
    AtomicReference<CompletableFuture<PeerConnection>> connectionFuture = new AtomicReference<>();
    connectionsById.compute(
        peer.getId(),
        (id, existingConnection) -> {
          if (existingConnection != null && !existingConnection.isFailedOrDisconnected()) {
            // We're already connected or connecting
            connectionFuture.set(existingConnection.getFuture());
            return existingConnection;
          } else {
            // We're initiating a new connection
            final CompletableFuture<PeerConnection> future = initiateOutboundConnection(peer);
            connectionFuture.set(future);
            RlpxConnection newConnection = RlpxConnection.outboundConnection(peer, future);
            newConnection.subscribeConnectionEstablished(
                (conn) -> {
                  this.dispatchConnect(conn.getPeerConnection());
                  this.enforceConnectionLimits();
                },
                (failedConn) -> cleanUpPeerConnection(failedConn.getId()));
            return newConnection;
          }
        });

    return connectionFuture.get();
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
    cleanUpPeerConnection(peerConnection.getPeer().getId());
  }

  private void cleanUpPeerConnection(final BytesValue peerId) {
    connectionsById.compute(
        peerId,
        (id, trackedConnection) -> {
          if (isNull(trackedConnection) || trackedConnection.isFailedOrDisconnected()) {
            // Remove if failed or disconnected
            return null;
          }
          return trackedConnection;
        });
  }

  private void handlePermissionsUpdate(
      final boolean permissionsRestricted, final Optional<List<Peer>> peers) {
    if (!permissionsRestricted) {
      // Nothing to do
      return;
    }

    final List<RlpxConnection> connectionsToCheck =
        peers
            .map(
                updatedPeers ->
                    updatedPeers.stream()
                        .map(peer -> connectionsById.get(peer.getId()))
                        .filter(connection -> !isNull(connection))
                        .collect(Collectors.toList()))
            .orElse(new ArrayList<>(connectionsById.values()));

    connectionsToCheck.forEach(
        connection -> {
          if (!peerPermissions.allowOngoingConnection(
              connection.getPeer(), connection.initiatedRemotely())) {
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

  private void handleIncomingConnection(final PeerConnection peerConnection) {
    final Peer peer = peerConnection.getPeer();
    // Deny connection if our local node isn't ready
    if (!localNode.isReady()) {
      peerConnection.disconnect(DisconnectReason.UNKNOWN);
      return;
    }
    // Disconnect if too many peers
    if (!peerPrivileges.canExceedConnectionLimits(peer) && getConnectionCount() >= maxConnections) {
      peerConnection.disconnect(DisconnectReason.TOO_MANY_PEERS);
      return;
    }
    // Disconnect if too many remotely-initiated connections
    if (!peerPrivileges.canExceedConnectionLimits(peer) && remoteConnectionLimitReached()) {
      peerConnection.disconnect(DisconnectReason.TOO_MANY_PEERS);
      return;
    }
    // Disconnect if not permitted
    if (!peerPermissions.allowNewInboundConnectionFrom(peer)) {
      peerConnection.disconnect(DisconnectReason.UNKNOWN);
      return;
    }

    // Track this new connection, deduplicating existing connection if necessary
    final AtomicBoolean newConnectionAccepted = new AtomicBoolean(false);
    final RlpxConnection inboundConnection = RlpxConnection.inboundConnection(peerConnection);
    // Our disconnect handler runs connectionsById.compute(), so don't actually execute the
    // disconnect command until we've returned from our compute() calculation
    final AtomicReference<Runnable> disconnectAction = new AtomicReference<>();
    connectionsById.compute(
        peer.getId(),
        (nodeId, existingConnection) -> {
          if (existingConnection == null) {
            // The new connection is unique, set it and return
            LOG.debug("Inbound connection established with {}", peerConnection.getPeer().getId());
            newConnectionAccepted.set(true);
            return inboundConnection;
          }
          // We already have an existing connection, figure out which connection to keep
          if (compareDuplicateConnections(inboundConnection, existingConnection) < 0) {
            // Keep the inbound connection
            LOG.debug(
                "Duplicate connection detected, disconnecting previously established connection in favor of new inbound connection for peer:  {}",
                peerConnection.getPeer().getId());
            disconnectAction.set(
                () -> existingConnection.disconnect(DisconnectReason.ALREADY_CONNECTED));
            newConnectionAccepted.set(true);
            return inboundConnection;
          } else {
            // Keep the existing connection
            LOG.debug(
                "Duplicate connection detected, disconnecting inbound connection in favor of previously established connection for peer:  {}",
                peerConnection.getPeer().getId());
            disconnectAction.set(
                () -> inboundConnection.disconnect(DisconnectReason.ALREADY_CONNECTED));
            return existingConnection;
          }
        });

    if (!isNull(disconnectAction.get())) {
      disconnectAction.get().run();
    }
    if (newConnectionAccepted.get()) {
      dispatchConnect(peerConnection);
    }
    // Check remote connections again to control for race conditions
    enforceRemoteConnectionLimits();
    enforceConnectionLimits();
  }

  private boolean shouldLimitRemoteConnections() {
    return maxRemotelyInitiatedConnections < maxConnections;
  }

  private boolean remoteConnectionLimitReached() {
    return shouldLimitRemoteConnections()
        && countUntrustedRemotelyInitiatedConnections() >= maxRemotelyInitiatedConnections;
  }

  private long countUntrustedRemotelyInitiatedConnections() {
    return connectionsById.values().stream()
        .filter(RlpxConnection::isActive)
        .filter(RlpxConnection::initiatedRemotely)
        .filter(conn -> !peerPrivileges.canExceedConnectionLimits(conn.getPeer()))
        .count();
  }

  private void enforceRemoteConnectionLimits() {
    if (!shouldLimitRemoteConnections()
        || connectionsById.size() < maxRemotelyInitiatedConnections) {
      // Nothing to do
      return;
    }

    getActivePrioritizedConnections()
        .filter(RlpxConnection::initiatedRemotely)
        .filter(conn -> !peerPrivileges.canExceedConnectionLimits(conn.getPeer()))
        .skip(maxRemotelyInitiatedConnections)
        .forEach(c -> c.disconnect(DisconnectReason.TOO_MANY_PEERS));
  }

  private void enforceConnectionLimits() {
    if (connectionsById.size() < maxConnections) {
      // Nothing to do - we're under our limits
      return;
    }

    getActivePrioritizedConnections()
        .skip(maxConnections)
        .filter(c -> !peerPrivileges.canExceedConnectionLimits(c.getPeer()))
        .forEach(c -> c.disconnect(DisconnectReason.TOO_MANY_PEERS));
  }

  private Stream<RlpxConnection> getActivePrioritizedConnections() {
    return connectionsById.values().stream()
        .filter(RlpxConnection::isActive)
        .sorted(this::prioritizeConnections);
  }

  private int prioritizeConnections(final RlpxConnection a, final RlpxConnection b) {
    final boolean aIgnoresPeerLimits = peerPrivileges.canExceedConnectionLimits(a.getPeer());
    final boolean bIgnoresPeerLimits = peerPrivileges.canExceedConnectionLimits(b.getPeer());
    if (aIgnoresPeerLimits && !bIgnoresPeerLimits) {
      return -1;
    } else if (bIgnoresPeerLimits && !aIgnoresPeerLimits) {
      return 1;
    } else {
      return Math.toIntExact(a.getInitiatedAt() - b.getInitiatedAt());
    }
  }

  /**
   * Compares two connections to the same peer to determine which connection should be kept
   *
   * @param a The first connection
   * @param b The second connection
   * @return A negative value if {@code a} should be kept, a positive value is {@code b} should be
   *     kept
   */
  private int compareDuplicateConnections(final RlpxConnection a, final RlpxConnection b) {
    checkState(localNode.isReady());
    checkState(Objects.equals(a.getPeer().getId(), b.getPeer().getId()));

    if (a.isFailedOrDisconnected() != b.isFailedOrDisconnected()) {
      // One connection has failed - prioritize the one that hasn't failed
      return a.isFailedOrDisconnected() ? 1 : -1;
    }

    final BytesValue peerId = a.getPeer().getId();
    final BytesValue localId = localNode.getPeer().getId();
    if (a.initiatedRemotely() != b.initiatedRemotely()) {
      // If we have connections initiated in different directions, keep the connection initiated
      // by the node with the lower id
      if (localId.compareTo(peerId) < 0) {
        return a.initiatedLocally() ? -1 : 1;
      } else {
        return a.initiatedLocally() ? 1 : -1;
      }
    }
    // Otherwise, keep older connection
    return Math.toIntExact(a.getInitiatedAt() - b.getInitiatedAt());
  }

  public void subscribeMessage(final Capability capability, final MessageCallback callback) {
    connectionEvents.subscribeMessage(capability, callback);
  }

  public void subscribeConnect(final ConnectCallback callback) {
    connectSubscribers.subscribe(callback);
  }

  public void subscribeDisconnect(final DisconnectCallback callback) {
    connectionEvents.subscribeDisconnect(callback);
  }

  private void dispatchConnect(final PeerConnection connection) {
    connectedPeersCounter.inc();
    connectSubscribers.forEach(c -> c.onConnect(connection));
  }

  public static class Builder {
    private KeyPair keyPair;
    private LocalNode localNode;
    private RlpxConfiguration config;
    private PeerPrivileges peerPrivileges;
    private PeerPermissions peerPermissions;
    private ConnectionInitializer connectionInitializer;
    private PeerConnectionEvents connectionEvents;
    private MetricsSystem metricsSystem;

    private Builder() {}

    public RlpxAgent build() {
      validate();

      if (connectionEvents == null) {
        connectionEvents = new PeerConnectionEvents(metricsSystem);
      }
      if (connectionInitializer == null) {
        connectionInitializer =
            new NettyConnectionInitializer(
                keyPair, config, localNode, connectionEvents, metricsSystem);
      }

      final PeerRlpxPermissions rlpxPermissions =
          new PeerRlpxPermissions(localNode, peerPermissions);
      return new RlpxAgent(
          localNode,
          connectionEvents,
          connectionInitializer,
          rlpxPermissions,
          peerPrivileges,
          config.getMaxPeers(),
          config.getMaxRemotelyInitiatedConnections(),
          metricsSystem);
    }

    private void validate() {
      checkState(keyPair != null, "KeyPair must be configured");
      checkState(localNode != null, "LocalNode must be configured");
      checkState(config != null, "RlpxConfiguration must be set");
      checkState(peerPrivileges != null, "PeerPrivileges must be configured");
      checkState(peerPermissions != null, "PeerPermissions must be configured");
      checkState(metricsSystem != null, "MetricsSystem must be configured");
    }

    public Builder keyPair(final KeyPair keyPair) {
      checkNotNull(keyPair);
      this.keyPair = keyPair;
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
  }
}
