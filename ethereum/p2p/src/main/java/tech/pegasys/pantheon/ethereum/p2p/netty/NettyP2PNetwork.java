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
import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryAgent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerRequirement;
import tech.pegasys.pantheon.ethereum.p2p.peers.Endpoint;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.Subscribers;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
 * <p>Upper layers in the stack subscribe to events from the peer discocvery layer and initiate/drop
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
public final class NettyP2PNetwork implements P2PNetwork {

  private static final Logger LOG = LogManager.getLogger();
  private static final int TIMEOUT_SECONDS = 30;

  final Map<Capability, Subscribers<Consumer<Message>>> protocolCallbacks =
      new ConcurrentHashMap<>();

  private final Subscribers<Consumer<PeerConnection>> connectCallbacks = new Subscribers<>();

  private final Subscribers<DisconnectCallback> disconnectCallbacks = new Subscribers<>();

  private final Callbacks callbacks = new Callbacks(protocolCallbacks, disconnectCallbacks);

  private final PeerDiscoveryAgent peerDiscoveryAgent;
  private final PeerBlacklist peerBlacklist;
  private OptionalLong peerBondedObserverId = OptionalLong.empty();

  private final PeerConnectionRegistry connections;

  private final AtomicInteger pendingConnections = new AtomicInteger(0);

  private final EventLoopGroup boss = new NioEventLoopGroup(1);

  private final EventLoopGroup workers = new NioEventLoopGroup(1);

  private volatile PeerInfo ourPeerInfo;

  private final SECP256K1.KeyPair keyPair;

  private final ChannelFuture server;

  private final int maxPeers;

  private final List<SubProtocol> subProtocols;

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
   * @param peerRequirement Queried to determine if enough peers are currently connected.
   * @param metricsSystem The metrics system to capture metrics with.
   */
  public NettyP2PNetwork(
      final Vertx vertx,
      final SECP256K1.KeyPair keyPair,
      final NetworkingConfiguration config,
      final List<Capability> supportedCapabilities,
      final PeerRequirement peerRequirement,
      final PeerBlacklist peerBlacklist,
      final MetricsSystem metricsSystem) {

    connections = new PeerConnectionRegistry(metricsSystem);
    this.peerBlacklist = peerBlacklist;
    peerDiscoveryAgent =
        new PeerDiscoveryAgent(
            vertx, keyPair, config.getDiscovery(), peerRequirement, peerBlacklist);
    subscribeDisconnect(peerDiscoveryAgent);
    subscribeDisconnect(peerBlacklist);
    subscribeDisconnect(connections);

    maxPeers = config.getRlpx().getMaxPeers();
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
                    keyPair, subProtocols, ourPeerInfo, connectionFuture, callbacks, connections));

        connectionFuture.thenAccept(
            connection -> {
              // Reject incoming connections if we've reached our limit
              if (connections.size() >= maxPeers) {
                LOG.debug(
                    "Disconnecting incoming connection because connection limit of {} has been reached: {}",
                    maxPeers,
                    connection.getPeer().getNodeId());
                connection.disconnect(DisconnectReason.TOO_MANY_PEERS);
                return;
              }
              // Reject incoming connections that are blacklisted
              if (peerBlacklist.contains(connection)) {
                connection.disconnect(DisconnectReason.USELESS_PEER);
                return;
              }

              onConnectionEstablished(connection);
              LOG.debug(
                  "Successfully accepted connection from {}", connection.getPeer().getNodeId());
              logConnections();
            });
      }
    };
  }

  private int connectionCount() {
    return pendingConnections.get() + connections.size();
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
    pendingConnections.incrementAndGet();

    new Bootstrap()
        .group(workers)
        .channel(NioSocketChannel.class)
        .remoteAddress(new InetSocketAddress(endpoint.getHost(), endpoint.getTcpPort().getAsInt()))
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
                            connections));
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
          pendingConnections.decrementAndGet();
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
        pendingConnections.get(),
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
  public void run() {
    try {
      peerDiscoveryAgent.start(ourPeerInfo.getPort()).join();
      final long observerId =
          peerDiscoveryAgent.observePeerBondedEvents(
              peerBondedEvent -> {
                if (connectionCount() < maxPeers
                    && peerBondedEvent.getPeer().getEndpoint().getTcpPort().isPresent()
                    && !connections.isAlreadyConnected(peerBondedEvent.getPeer().getId())) {
                  connect(peerBondedEvent.getPeer());
                }
              });
      peerBondedObserverId = OptionalLong.of(observerId);
    } catch (final Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public void stop() {
    sendClientQuittingToPeers();
    peerDiscoveryAgent.stop().join();
    peerBondedObserverId.ifPresent(peerDiscoveryAgent::removePeerBondedObserver);
    peerBondedObserverId = OptionalLong.empty();
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

  public Collection<DiscoveryPeer> getDiscoveryPeers() {
    return peerDiscoveryAgent.getPeers();
  }

  @Override
  public InetSocketAddress getDiscoverySocketAddress() {
    return peerDiscoveryAgent.localAddress();
  }

  @Override
  public PeerInfo getSelf() {
    return ourPeerInfo;
  }

  @Override
  public boolean isListening() {
    return peerDiscoveryAgent.isActive();
  }

  private void onConnectionEstablished(final PeerConnection connection) {
    connections.registerConnection(connection);
    connectCallbacks.forEach(callback -> callback.accept(connection));
  }
}
