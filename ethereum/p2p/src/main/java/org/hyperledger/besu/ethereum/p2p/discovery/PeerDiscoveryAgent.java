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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.tuweni.bytes.Bytes.wrapBuffer;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController.AsyncExecutor;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerRequirement;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PingPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.TimerUtil;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.Subscribers;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * The peer discovery agent is the network component that sends and receives peer discovery messages
 * via UDP.
 */
public abstract class PeerDiscoveryAgent {
  protected static final Logger LOG = LogManager.getLogger();

  // The devp2p specification says only accept packets up to 1280, but some
  // clients ignore that, so we add in a little extra padding.
  private static final int MAX_PACKET_SIZE_BYTES = 1600;

  protected final List<DiscoveryPeer> bootstrapPeers;
  private final List<PeerRequirement> peerRequirements = new CopyOnWriteArrayList<>();
  private final PeerPermissions peerPermissions;
  private final NatService natService;
  private final MetricsSystem metricsSystem;
  /* The peer controller, which takes care of the state machine of peers. */
  protected Optional<PeerDiscoveryController> controller = Optional.empty();

  /* The keypair used to sign messages. */
  protected final NodeKey nodeKey;
  private final Bytes id;
  protected final DiscoveryConfiguration config;

  /* This is the {@link org.hyperledger.besu.ethereum.p2p.Peer} object representing our local node.
   * This value is empty on construction, and is set after the discovery agent is started.
   */
  private Optional<DiscoveryPeer> localNode = Optional.empty();

  /* Is discovery enabled? */
  private boolean isActive = false;
  protected final Subscribers<PeerBondedObserver> peerBondedObservers = Subscribers.create();

  public PeerDiscoveryAgent(
      final NodeKey nodeKey,
      final DiscoveryConfiguration config,
      final PeerPermissions peerPermissions,
      final NatService natService,
      final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    checkArgument(nodeKey != null, "nodeKey cannot be null");
    checkArgument(config != null, "provided configuration cannot be null");

    validateConfiguration(config);

    this.peerPermissions = peerPermissions;
    this.natService = natService;
    this.bootstrapPeers =
        config.getBootnodes().stream().map(DiscoveryPeer::fromEnode).collect(Collectors.toList());

    this.config = config;
    this.nodeKey = nodeKey;

    id = nodeKey.getPublicKey().getEncodedBytes();
  }

  protected abstract TimerUtil createTimer();

  protected abstract AsyncExecutor createWorkerExecutor();

  protected abstract CompletableFuture<InetSocketAddress> listenForConnections();

  protected abstract CompletableFuture<Void> sendOutgoingPacket(
      final DiscoveryPeer peer, final Packet packet);

  public abstract CompletableFuture<?> stop();

  public CompletableFuture<Integer> start(final int tcpPort) {
    if (config.isActive()) {
      final String host = config.getBindHost();
      final int port = config.getBindPort();
      LOG.info("Starting peer discovery agent on host={}, port={}", host, port);

      // override advertised host if we detect an external IP address via NAT manager
      final String advertisedAddress =
          natService.queryExternalIPAddress(config.getAdvertisedHost());

      return listenForConnections()
          .thenApply(
              (InetSocketAddress localAddress) -> {
                // Once listener is set up, finish initializing
                final int discoveryPort = localAddress.getPort();
                final DiscoveryPeer ourNode =
                    DiscoveryPeer.fromEnode(
                        EnodeURL.builder()
                            .nodeId(id)
                            .ipAddress(advertisedAddress)
                            .listeningPort(tcpPort)
                            .discoveryPort(discoveryPort)
                            .build());
                this.localNode = Optional.of(ourNode);
                isActive = true;
                LOG.info("P2P peer discovery agent started and listening on {}", localAddress);
                startController(ourNode);
                return discoveryPort;
              });
    } else {
      this.isActive = false;
      return CompletableFuture.completedFuture(0);
    }
  }

  public void addPeerRequirement(final PeerRequirement peerRequirement) {
    this.peerRequirements.add(peerRequirement);
  }

  private void startController(final DiscoveryPeer localNode) {
    final PeerDiscoveryController controller = createController(localNode);
    this.controller = Optional.of(controller);
    controller.start();
  }

  private PeerDiscoveryController createController(final DiscoveryPeer localNode) {
    return PeerDiscoveryController.builder()
        .nodeKey(nodeKey)
        .localPeer(localNode)
        .bootstrapNodes(bootstrapPeers)
        .outboundMessageHandler(this::handleOutgoingPacket)
        .timerUtil(createTimer())
        .workerExecutor(createWorkerExecutor())
        .peerRequirement(PeerRequirement.combine(peerRequirements))
        .peerPermissions(peerPermissions)
        .peerBondedObservers(peerBondedObservers)
        .metricsSystem(metricsSystem)
        .build();
  }

  protected boolean validatePacketSize(final int packetSize) {
    return packetSize <= MAX_PACKET_SIZE_BYTES;
  }

  protected void handleIncomingPacket(final Endpoint sourceEndpoint, final Packet packet) {
    OptionalInt tcpPort = OptionalInt.empty();
    if (packet.getPacketData(PingPacketData.class).isPresent()) {
      final PingPacketData ping = packet.getPacketData(PingPacketData.class).orElseGet(null);
      if (ping != null && ping.getFrom() != null && ping.getFrom().getTcpPort().isPresent()) {
        tcpPort = ping.getFrom().getTcpPort();
      }
    }

    // Notify the peer controller.
    String host = sourceEndpoint.getHost();
    int port = sourceEndpoint.getUdpPort();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromEnode(
            EnodeURL.builder()
                .nodeId(packet.getNodeId())
                .ipAddress(host)
                .listeningPort(tcpPort.orElse(port))
                .discoveryPort(port)
                .build());

    controller.ifPresent(c -> c.onMessage(packet, peer));
  }

  /**
   * Send a packet to the given recipient.
   *
   * @param peer the recipient
   * @param packet the packet to send
   */
  protected void handleOutgoingPacket(final DiscoveryPeer peer, final Packet packet) {
    sendOutgoingPacket(peer, packet)
        .whenComplete(
            (res, err) -> {
              if (err != null) {
                if (err instanceof SocketException && err.getMessage().contains("unreachable")) {
                  LOG.debug(
                      "Peer {} is unreachable, packet: {}", peer, wrapBuffer(packet.encode()), err);
                } else {
                  LOG.warn(
                      "Sending to peer {} failed, packet: {}",
                      peer,
                      wrapBuffer(packet.encode()),
                      err);
                }
                return;
              }
              peer.setLastContacted(System.currentTimeMillis());
            });
  }

  public Stream<DiscoveryPeer> streamDiscoveredPeers() {
    return controller.map(PeerDiscoveryController::streamDiscoveredPeers).orElse(Stream.empty());
  }

  public void dropPeer(final PeerId peer) {
    controller.ifPresent(c -> c.dropPeer(peer));
  }

  public Optional<DiscoveryPeer> getAdvertisedPeer() {
    return localNode;
  }

  public Bytes getId() {
    return id;
  }

  /**
   * Adds an observer that will get called when a new peer is bonded with and added to the peer
   * table.
   *
   * <p><i>No guarantees are made about the order in which observers are invoked.</i>
   *
   * @param observer The observer to call.
   * @return A unique ID identifying this observer, to that it can be removed later.
   */
  public long observePeerBondedEvents(final PeerBondedObserver observer) {
    checkNotNull(observer);
    return peerBondedObservers.subscribe(observer);
  }

  /**
   * Removes an previously added peer bonded observer.
   *
   * @param observerId The unique ID identifying the observer to remove.
   * @return Whether the observer was located and removed.
   */
  public boolean removePeerBondedObserver(final long observerId) {
    return peerBondedObservers.unsubscribe(observerId);
  }

  /**
   * Returns the count of observers that are registered on this controller.
   *
   * @return The observer count.
   */
  @VisibleForTesting
  public int getObserverCount() {
    return peerBondedObservers.getSubscriberCount();
  }

  private static void validateConfiguration(final DiscoveryConfiguration config) {
    checkArgument(
        config.getBindHost() != null && InetAddresses.isInetAddress(config.getBindHost()),
        "valid bind host required");
    checkArgument(
        config.getAdvertisedHost() != null
            && InetAddresses.isInetAddress(config.getAdvertisedHost()),
        "valid advertisement host required");
    checkArgument(
        config.getBindPort() == 0 || NetworkUtility.isValidPort(config.getBindPort()),
        "valid port number required");
    checkArgument(config.getBootnodes() != null, "bootstrapPeers cannot be null");
    checkArgument(config.getBucketSize() > 0, "bucket size cannot be negative nor zero");
  }

  /**
   * Returns the current state of the PeerDiscoveryAgent.
   *
   * <p>If true, the node is actively listening for new connections. If false, discovery has been
   * turned off and the node is not listening for connections.
   *
   * @return true, if the {@link PeerDiscoveryAgent} is active on this node, false, otherwise.
   */
  public boolean isActive() {
    return isActive;
  }
}
