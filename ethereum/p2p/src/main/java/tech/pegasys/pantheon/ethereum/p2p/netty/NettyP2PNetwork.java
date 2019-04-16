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
package tech.pegasys.pantheon.ethereum.p2p.netty;

import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.p2p.ConnectingToLocalNodeException;
import tech.pegasys.pantheon.ethereum.p2p.PeerNotPermittedException;
import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerDroppedEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.ethereum.p2p.discovery.VertxPeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.node.NodePermissioningController;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.annotations.VisibleForTesting;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The peer network service (defunct PeerNetworkingService) is the entrypoint to the peer-to-peer
 * components of the Ethereum client. It implements the devp2p framework from the Ethereum
 * specifications.
 *
 * <p>This component manages the peer discovery layer, the RLPx wire protocol and the subprotocols
 * supported by this client.
 *
 * <h2>Peer Discovery</h2>
 *
 * Ethereum nodes discover one another via a simple UDP protocol that follows some of the techniques
 * described in the Kademlia DHT paper. Particularly nodes are classified in a k-bucket table
 * composed of 256 buckets, where each bucket contains at most 16 peers whose <i>XOR(SHA3(x))</i>
 * distance from us is equal to the index of the bucket. The value <i>x</i> in the distance function
 * corresponds to our node ID (public key).
 *
 * <p>Upper layers in the stack subscribe to events from the peer discovery layer and initiate/drop
 * connections accordingly.
 *
 * <h2>RLPx Wire Protocol</h2>
 *
 * The RLPx wire protocol is responsible for selecting peers to engage with, authenticating and
 * encrypting communications with peers, multiplexing subprotocols, framing messages, controlling
 * legality of messages, keeping connections alive, and keeping track of peer reputation.
 *
 * <h2>Subprotocols</h2>
 *
 * Subprotocols are pluggable elements on top of the RLPx framework, which can handle a specific set
 * of messages. Each subprotocol has a 3-char ASCII denominator and a version number, and statically
 * defines a count of messages it can handle.
 *
 * <p>The RLPx wire protocol dispatches messages to subprotocols based on the capabilities agreed by
 * each of the two peers during the protocol handshake.
 *
 * @see <a href="https://pdos.csail.mit.edu/~petar/papers/maymounkov-kademlia-lncs.pdf">Kademlia DHT
 *     paper</a>
 * @see <a href="https://github.com/ethereum/wiki/wiki/Kademlia-Peer-Selection">Kademlia Peer
 *     Selection</a>
 * @see <a href="https://github.com/ethereum/devp2p/blob/master/rlpx.md">devp2p RLPx</a>
 */
public class NettyP2PNetwork implements P2PNetwork {

  private static final Logger LOG = LogManager.getLogger();
  private static final int TIMEOUT_SECONDS = 30;

  private final ScheduledExecutorService peerConnectionScheduler =
      Executors.newSingleThreadScheduledExecutor();

  final Map<Capability, Subscribers<Consumer<Message>>> protocolCallbacks =
      new ConcurrentHashMap<>();

  private final Subscribers<Consumer<PeerConnection>> connectCallbacks = new Subscribers<>();

  private final Subscribers<DisconnectCallback> disconnectCallbacks = new Subscribers<>();

  private final Callbacks callbacks = new Callbacks(protocolCallbacks, disconnectCallbacks);

  private final PeerDiscoveryAgent peerDiscoveryAgent;
  private final PeerBlacklist peerBlacklist;
  private final NetworkingConfiguration config;
  private OptionalLong peerBondedObserverId = OptionalLong.empty();
  private OptionalLong peerDroppedObserverId = OptionalLong.empty();

  @VisibleForTesting public final Collection<Peer> peerMaintainConnectionList;

  @VisibleForTesting public final PeerConnectionRegistry connections;

  @VisibleForTesting
  public final Map<Peer, CompletableFuture<PeerConnection>> pendingConnections =
      new ConcurrentHashMap<>();

  private final EventLoopGroup boss = new NioEventLoopGroup(1);

  private final EventLoopGroup workers = new NioEventLoopGroup(1);

  private volatile PeerInfo ourPeerInfo;

  private final SECP256K1.KeyPair keyPair;

  private final ChannelFuture server;

  private final int maxPeers;

  private final List<SubProtocol> subProtocols;

  private final LabelledMetric<Counter> outboundMessagesCounter;

  private final String advertisedHost;

  private volatile EnodeURL ourEnodeURL;

  private final Optional<NodePermissioningController> nodePermissioningController;
  private final Optional<Blockchain> blockchain;
  private OptionalLong blockAddedObserverId = OptionalLong.empty();

  public NettyP2PNetwork(
      final Vertx vertx,
      final KeyPair keyPair,
      final NetworkingConfiguration config,
      final List<Capability> supportedCapabilities,
      final PeerBlacklist peerBlacklist,
      final MetricsSystem metricsSystem,
      final Optional<NodeLocalConfigPermissioningController> nodeWhitelistController,
      final Optional<NodePermissioningController> nodePermissioningController) {
    this(
        vertx,
        keyPair,
        config,
        supportedCapabilities,
        peerBlacklist,
        metricsSystem,
        nodeWhitelistController,
        nodePermissioningController,
        null);
  }

  /**
   * Creates a peer networking service for production purposes.
   *
   * <p>The caller is expected to provide the IP address to be advertised (normally this node's
   * public IP address), as well as TCP and UDP port numbers for the RLPx agent and the discovery
   * agent, respectively.
   *
   * @param vertx The vertx instance.
   * @param keyPair This node's keypair.
   * @param config The network configuration to use.
   * @param supportedCapabilities The wire protocol capabilities to advertise to connected peers.
   * @param peerBlacklist The peers with which this node will not connect
   * @param metricsSystem The metrics system to capture metrics with.
   * @param nodeLocalConfigPermissioningController local file config for permissioning
   * @param nodePermissioningController Controls node permissioning.
   * @param blockchain The blockchain to subscribe to BlockAddedEvents.
   */
  public NettyP2PNetwork(
      final Vertx vertx,
      final SECP256K1.KeyPair keyPair,
      final NetworkingConfiguration config,
      final List<Capability> supportedCapabilities,
      final PeerBlacklist peerBlacklist,
      final MetricsSystem metricsSystem,
      final Optional<NodeLocalConfigPermissioningController> nodeLocalConfigPermissioningController,
      final Optional<NodePermissioningController> nodePermissioningController,
      final Blockchain blockchain) {

    this.config = config;
    maxPeers = config.getRlpx().getMaxPeers();
    connections = new PeerConnectionRegistry(metricsSystem);
    this.peerBlacklist = peerBlacklist;
    this.peerMaintainConnectionList = new HashSet<>();
    peerDiscoveryAgent =
        new VertxPeerDiscoveryAgent(
            vertx,
            keyPair,
            config.getDiscovery(),
            () -> connections.size() >= maxPeers,
            peerBlacklist,
            nodeLocalConfigPermissioningController,
            nodePermissioningController,
            metricsSystem);

    outboundMessagesCounter =
        metricsSystem.createLabelledCounter(
            MetricCategory.NETWORK,
            "p2p_messages_outbound",
            "Count of each P2P message sent outbound.",
            "protocol",
            "name",
            "code");

    metricsSystem.createIntegerGauge(
        MetricCategory.NETWORK,
        "netty_workers_pending_tasks",
        "The number of pending tasks in the Netty workers event loop",
        pendingTaskCounter(workers));

    metricsSystem.createIntegerGauge(
        MetricCategory.NETWORK,
        "netty_boss_pending_tasks",
        "The number of pending tasks in the Netty boss event loop",
        pendingTaskCounter(boss));

    metricsSystem.createIntegerGauge(
        MetricCategory.NETWORK,
        "vertx_eventloop_pending_tasks",
        "The number of pending tasks in the Vertx event loop",
        pendingTaskCounter(vertx.nettyEventLoopGroup()));

    subscribeDisconnect(peerDiscoveryAgent);
    subscribeDisconnect(peerBlacklist);
    subscribeDisconnect(connections);

    this.keyPair = keyPair;
    this.subProtocols = config.getSupportedProtocols();

    server =
        new ServerBootstrap()
            .group(boss, workers)
            .channel(NioServerSocketChannel.class)
            .childHandler(inboundChannelInitializer())
            .bind(config.getRlpx().getBindHost(), config.getRlpx().getBindPort());
    final CountDownLatch latch = new CountDownLatch(1);
    server.addListener(
        future -> {
          final InetSocketAddress socketAddress =
              (InetSocketAddress) server.channel().localAddress();
          final String message =
              String.format(
                  "Unable start up P2P network on %s:%s.  Check for port conflicts.",
                  config.getRlpx().getBindHost(), config.getRlpx().getBindPort());

          if (!future.isSuccess()) {
            LOG.error(message, future.cause());
          }
          checkState(socketAddress != null, message);
          ourPeerInfo =
              new PeerInfo(
                  5,
                  config.getClientId(),
                  supportedCapabilities,
                  socketAddress.getPort(),
                  this.keyPair.getPublicKey().getEncodedBytes());
          LOG.info("P2PNetwork started and listening on {}", socketAddress);
          latch.countDown();
        });

    // Ensure ourPeerInfo has been set prior to returning from the constructor.
    try {
      if (!latch.await(1, TimeUnit.MINUTES)) {
        throw new RuntimeException("Timed out while waiting for network startup");
      }
    } catch (final InterruptedException e) {
      throw new RuntimeException("Interrupted before startup completed", e);
    }

    this.nodePermissioningController = nodePermissioningController;
    this.blockchain = Optional.ofNullable(blockchain);
    this.advertisedHost = config.getDiscovery().getAdvertisedHost();
    this.nodePermissioningController.ifPresent(
        c -> c.subscribeToUpdates(this::checkCurrentConnections));
  }

  private Supplier<Integer> pendingTaskCounter(final EventLoopGroup eventLoopGroup) {
    return () ->
        StreamSupport.stream(eventLoopGroup.spliterator(), false)
            .filter(eventExecutor -> eventExecutor instanceof SingleThreadEventExecutor)
            .mapToInt(eventExecutor -> ((SingleThreadEventExecutor) eventExecutor).pendingTasks())
            .sum();
  }

  /** @return a channel initializer for inbound connections */
  public ChannelInitializer<SocketChannel> inboundChannelInitializer() {
    return new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(final SocketChannel ch) {
        final CompletableFuture<PeerConnection> connectionFuture = new CompletableFuture<>();
        ch.pipeline()
            .addLast(
                new TimeoutHandler<>(
                    connectionFuture::isDone,
                    TIMEOUT_SECONDS,
                    () ->
                        connectionFuture.completeExceptionally(
                            new TimeoutException(
                                "Timed out waiting to fully establish incoming connection"))),
                new HandshakeHandlerInbound(
                    keyPair,
                    subProtocols,
                    ourPeerInfo,
                    connectionFuture,
                    callbacks,
                    connections,
                    outboundMessagesCounter));

        connectionFuture.thenAccept(
            connection -> {
              // Reject incoming connections if we've reached our limit
              if (connections.size() >= maxPeers) {
                LOG.debug(
                    "Disconnecting incoming connection because connection limit of {} has been reached: {}",
                    maxPeers,
                    connection.getPeerInfo().getNodeId());
                connection.disconnect(DisconnectReason.TOO_MANY_PEERS);
                return;
              }

              if (!isPeerConnectionAllowed(connection)) {
                connection.disconnect(DisconnectReason.UNKNOWN);
                return;
              }

              onConnectionEstablished(connection);
              LOG.debug(
                  "Successfully accepted connection from {}", connection.getPeerInfo().getNodeId());
              logConnections();
            });
      }
    };
  }

  @Override
  public boolean addMaintainConnectionPeer(final Peer peer) {
    if (!isPeerAllowed(peer)) {
      throw new PeerNotPermittedException();
    }

    if (peer.getId().equals(ourPeerInfo.getNodeId())) {
      throw new ConnectingToLocalNodeException();
    }

    final boolean added = peerMaintainConnectionList.add(peer);
    if (added) {
      connect(peer);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean removeMaintainedConnectionPeer(final Peer peer) {
    final boolean removed = peerMaintainConnectionList.remove(peer);

    final CompletableFuture<PeerConnection> connectionFuture = pendingConnections.get(peer);
    if (connectionFuture != null) {
      connectionFuture.thenAccept(connection -> connection.disconnect(DisconnectReason.REQUESTED));
    }

    final Optional<PeerConnection> peerConnection = connections.getConnectionForPeer(peer.getId());
    peerConnection.ifPresent(pc -> pc.disconnect(DisconnectReason.REQUESTED));

    return removed;
  }

  public void checkMaintainedConnectionPeers() {
    for (final Peer peer : peerMaintainConnectionList) {
      if (!(isConnecting(peer) || isConnected(peer))) {
        connect(peer);
      }
    }
  }

  @VisibleForTesting
  void attemptPeerConnections() {
    final int availablePeerSlots = Math.max(0, maxPeers - connectionCount());
    if (availablePeerSlots <= 0) {
      return;
    }

    final List<DiscoveryPeer> peers =
        getDiscoveryPeers()
            .filter(peer -> peer.getStatus() == PeerDiscoveryStatus.BONDED)
            .filter(peer -> !isConnected(peer) && !isConnecting(peer))
            .collect(Collectors.toList());
    Collections.shuffle(peers);
    if (peers.size() == 0) {
      return;
    }

    LOG.trace(
        "Initiating connection to {} peers from the peer table",
        Math.min(availablePeerSlots, peers.size()));
    peers.stream().limit(availablePeerSlots).forEach(this::connect);
  }

  @VisibleForTesting
  int connectionCount() {
    return pendingConnections.size() + connections.size();
  }

  @Override
  public Collection<PeerConnection> getPeers() {
    return connections.getPeerConnections();
  }

  @Override
  public CompletableFuture<PeerConnection> connect(final Peer peer) {
    LOG.trace("Initiating connection to peer: {}", peer.getId());
    final CompletableFuture<PeerConnection> connectionFuture = new CompletableFuture<>();
    final Endpoint endpoint = peer.getEndpoint();
    final CompletableFuture<PeerConnection> existingPendingConnection =
        pendingConnections.putIfAbsent(peer, connectionFuture);
    if (existingPendingConnection != null) {
      LOG.debug("Attempted to connect to peer with pending connection: {}", peer.getId());
      return existingPendingConnection;
    }

    new Bootstrap()
        .group(workers)
        .channel(NioSocketChannel.class)
        .remoteAddress(new InetSocketAddress(endpoint.getHost(), endpoint.getFunctionalTcpPort()))
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT_SECONDS * 1000)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(final SocketChannel ch) {
                ch.pipeline()
                    .addLast(
                        new TimeoutHandler<>(
                            connectionFuture::isDone,
                            TIMEOUT_SECONDS,
                            () ->
                                connectionFuture.completeExceptionally(
                                    new TimeoutException(
                                        "Timed out waiting to establish connection with peer: "
                                            + peer.getId()))),
                        new HandshakeHandlerOutbound(
                            keyPair,
                            peer.getId(),
                            subProtocols,
                            ourPeerInfo,
                            connectionFuture,
                            callbacks,
                            connections,
                            outboundMessagesCounter));
              }
            })
        .connect()
        .addListener(
            (f) -> {
              if (!f.isSuccess()) {
                connectionFuture.completeExceptionally(f.cause());
              }
            });

    connectionFuture.whenComplete(
        (connection, t) -> {
          pendingConnections.remove(peer);
          if (t == null) {
            onConnectionEstablished(connection);
            LOG.debug("Connection established to peer: {}", peer.getId());
          } else {
            LOG.debug("Failed to connect to peer {}: {}", peer.getId(), t);
          }
          logConnections();
        });
    return connectionFuture;
  }

  private void logConnections() {
    LOG.debug(
        "Connections: {} pending, {} active connections.",
        pendingConnections.size(),
        connections.size());
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
  public void start() {
    peerDiscoveryAgent.start(ourPeerInfo.getPort()).join();
    peerBondedObserverId =
        OptionalLong.of(peerDiscoveryAgent.observePeerBondedEvents(handlePeerBondedEvent()));
    peerDroppedObserverId =
        OptionalLong.of(peerDiscoveryAgent.observePeerDroppedEvents(handlePeerDroppedEvents()));

    if (nodePermissioningController.isPresent()) {
      if (blockchain.isPresent()) {
        synchronized (this) {
          if (!blockAddedObserverId.isPresent()) {
            blockAddedObserverId =
                OptionalLong.of(blockchain.get().observeBlockAdded(this::handleBlockAddedEvent));
          }
        }
      } else {
        throw new IllegalStateException(
            "NettyP2PNetwork permissioning needs to listen to BlockAddedEvents. Blockchain can't be null.");
      }
    }

    this.ourEnodeURL = buildSelfEnodeURL();
    LOG.info("Enode URL {}", ourEnodeURL.toString());

    peerConnectionScheduler.scheduleWithFixedDelay(
        this::checkMaintainedConnectionPeers, 60, 60, TimeUnit.SECONDS);
    peerConnectionScheduler.scheduleWithFixedDelay(
        this::attemptPeerConnections, 30, 30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  Consumer<PeerBondedEvent> handlePeerBondedEvent() {
    return event -> {
      final Peer peer = event.getPeer();
      if (connectionCount() < maxPeers && !isConnecting(peer) && !isConnected(peer)) {
        connect(peer);
      }
    };
  }

  private Consumer<PeerDroppedEvent> handlePeerDroppedEvents() {
    return event -> {
      final Peer peer = event.getPeer();
      getPeers().stream()
          .filter(p -> p.getPeerInfo().getNodeId().equals(peer.getId()))
          .findFirst()
          .ifPresent(p -> p.disconnect(DisconnectReason.REQUESTED));
    };
  }

  private synchronized void handleBlockAddedEvent(
      final BlockAddedEvent event, final Blockchain blockchain) {
    connections
        .getPeerConnections()
        .forEach(
            peerConnection -> {
              if (!isPeerConnectionAllowed(peerConnection)) {
                peerConnection.disconnect(DisconnectReason.REQUESTED);
              }
            });
  }

  private synchronized void checkCurrentConnections() {
    connections
        .getPeerConnections()
        .forEach(
            peerConnection -> {
              if (!isPeerConnectionAllowed(peerConnection)) {
                peerConnection.disconnect(DisconnectReason.REQUESTED);
              }
            });
  }

  private boolean isPeerConnectionAllowed(final PeerConnection peerConnection) {
    if (peerBlacklist.contains(peerConnection)) {
      return false;
    }

    LOG.trace(
        "Checking if connection with peer {} is permitted",
        peerConnection.getPeerInfo().getNodeId());

    return nodePermissioningController
        .map(
            c -> {
              final EnodeURL localPeerEnodeURL = getLocalEnode().orElse(buildSelfEnodeURL());
              final EnodeURL remotePeerEnodeURL = peerConnection.getRemoteEnode();
              return c.isPermitted(localPeerEnodeURL, remotePeerEnodeURL);
            })
        .orElse(true);
  }

  private boolean isPeerAllowed(final Peer peer) {
    if (peerBlacklist.contains(peer)) {
      return false;
    }

    return nodePermissioningController
        .map(c -> c.isPermitted(ourEnodeURL, peer.getEnodeURL()))
        .orElse(true);
  }

  @VisibleForTesting
  boolean isConnecting(final Peer peer) {
    return pendingConnections.containsKey(peer);
  }

  @VisibleForTesting
  boolean isConnected(final Peer peer) {
    return connections.isAlreadyConnected(peer.getId());
  }

  @Override
  public void stop() {
    sendClientQuittingToPeers();
    peerConnectionScheduler.shutdownNow();
    peerDiscoveryAgent.stop().join();
    peerBondedObserverId.ifPresent(peerDiscoveryAgent::removePeerBondedObserver);
    peerBondedObserverId = OptionalLong.empty();
    peerDroppedObserverId.ifPresent(peerDiscoveryAgent::removePeerDroppedObserver);
    peerDroppedObserverId = OptionalLong.empty();
    blockchain.ifPresent(b -> blockAddedObserverId.ifPresent(b::removeObserver));
    blockAddedObserverId = OptionalLong.empty();
    peerDiscoveryAgent.stop().join();
    workers.shutdownGracefully();
    boss.shutdownGracefully();
  }

  private void sendClientQuittingToPeers() {
    connections.getPeerConnections().forEach(p -> p.disconnect(DisconnectReason.CLIENT_QUITTING));
  }

  @Override
  public void awaitStop() {
    try {
      server.channel().closeFuture().sync();
    } catch (final InterruptedException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public void close() {
    stop();
  }

  @VisibleForTesting
  public Stream<DiscoveryPeer> getDiscoveryPeers() {
    return peerDiscoveryAgent.getPeers();
  }

  @Override
  public boolean isListening() {
    return peerDiscoveryAgent.isActive();
  }

  @Override
  public boolean isP2pEnabled() {
    return true;
  }

  @Override
  public boolean isDiscoveryEnabled() {
    return config.getDiscovery().isActive();
  }

  @Override
  public Optional<EnodeURL> getLocalEnode() {
    return Optional.ofNullable(ourEnodeURL);
  }

  private EnodeURL buildSelfEnodeURL() {
    final BytesValue nodeId = ourPeerInfo.getNodeId();
    final int listeningPort = ourPeerInfo.getPort();
    final OptionalInt discoveryPort =
        peerDiscoveryAgent
            .getAdvertisedPeer()
            .map(p -> OptionalInt.of(p.getEndpoint().getUdpPort()))
            .filter(port -> port.getAsInt() != listeningPort)
            .orElse(OptionalInt.empty());

    return EnodeURL.builder()
        .nodeId(nodeId)
        .ipAddress(advertisedHost)
        .listeningPort(listeningPort)
        .discoveryPort(discoveryPort)
        .build();
  }

  private void onConnectionEstablished(final PeerConnection connection) {
    connections.registerConnection(connection);
    connectCallbacks.forEach(callback -> callback.accept(connection));
  }
}
