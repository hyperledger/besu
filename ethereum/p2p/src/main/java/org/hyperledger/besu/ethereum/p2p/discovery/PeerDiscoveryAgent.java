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
import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerDiscoveryController.AsyncExecutor;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PeerRequirement;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.PingPacketData;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.TimerUtil;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.NetworkUtility;
import org.hyperledger.besu.util.Subscribers;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.net.InetAddresses;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The peer discovery agent is the network component that sends and receives peer discovery messages
 * via UDP.
 */
public abstract class PeerDiscoveryAgent {
  private static final Logger LOG = LoggerFactory.getLogger(PeerDiscoveryAgent.class);
  private static final String SEQ_NO_STORE_KEY = "local-enr-seqno";
  private static final com.google.common.base.Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

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

  private final StorageProvider storageProvider;
  private final Supplier<List<Bytes>> forkIdSupplier;
  private String advertisedAddress;

  protected PeerDiscoveryAgent(
      final NodeKey nodeKey,
      final DiscoveryConfiguration config,
      final PeerPermissions peerPermissions,
      final NatService natService,
      final MetricsSystem metricsSystem,
      final StorageProvider storageProvider,
      final Supplier<List<Bytes>> forkIdSupplier) {
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

    this.id = nodeKey.getPublicKey().getEncodedBytes();

    this.storageProvider = storageProvider;
    this.forkIdSupplier = forkIdSupplier;
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
      this.advertisedAddress = natService.queryExternalIPAddress(config.getAdvertisedHost());

      return listenForConnections()
          .thenApply(
              (InetSocketAddress localAddress) -> {
                // Once listener is set up, finish initializing
                final int discoveryPort = localAddress.getPort();
                final DiscoveryPeer ourNode =
                    DiscoveryPeer.fromEnode(
                        EnodeURLImpl.builder()
                            .nodeId(id)
                            .ipAddress(advertisedAddress)
                            .listeningPort(tcpPort)
                            .discoveryPort(discoveryPort)
                            .build());
                this.localNode = Optional.of(ourNode);
                isActive = true;
                LOG.info("P2P peer discovery agent started and listening on {}", localAddress);
                updateNodeRecord();
                startController(ourNode);
                return discoveryPort;
              });
    } else {
      this.isActive = false;
      return CompletableFuture.completedFuture(0);
    }
  }

  public void updateNodeRecord() {
    if (!config.isActive()) {
      return;
    }

    final KeyValueStorage keyValueStorage =
        storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);
    final NodeRecordFactory nodeRecordFactory = NodeRecordFactory.DEFAULT;
    final Optional<NodeRecord> existingNodeRecord =
        keyValueStorage
            .get(Bytes.of(SEQ_NO_STORE_KEY.getBytes(UTF_8)).toArray())
            .map(Bytes::of)
            .map(nodeRecordFactory::fromBytes);

    final Bytes addressBytes = Bytes.of(InetAddresses.forString(advertisedAddress).getAddress());
    final Optional<EnodeURL> maybeEnodeURL = localNode.map(DiscoveryPeer::getEnodeURL);
    final Integer discoveryPort = maybeEnodeURL.flatMap(EnodeURL::getDiscoveryPort).orElse(0);
    final Integer listeningPort = maybeEnodeURL.flatMap(EnodeURL::getListeningPort).orElse(0);
    final String forkIdEnrField = "eth";
    final NodeRecord newNodeRecord =
        existingNodeRecord
            .filter(
                nodeRecord ->
                    id.equals(nodeRecord.get(EnrField.PKEY_SECP256K1))
                        && addressBytes.equals(nodeRecord.get(EnrField.IP_V4))
                        && discoveryPort.equals(nodeRecord.get(EnrField.UDP))
                        && listeningPort.equals(nodeRecord.get(EnrField.TCP))
                        && forkIdSupplier.get().equals(nodeRecord.get(forkIdEnrField)))
            .orElseGet(
                () -> {
                  final UInt64 sequenceNumber =
                      existingNodeRecord.map(NodeRecord::getSeq).orElse(UInt64.ZERO).add(1);
                  final NodeRecord nodeRecord =
                      nodeRecordFactory.createFromValues(
                          sequenceNumber,
                          new EnrField(EnrField.ID, IdentitySchema.V4),
                          new EnrField(
                              SIGNATURE_ALGORITHM.get().getCurveName(),
                              SIGNATURE_ALGORITHM
                                  .get()
                                  .compressPublicKey(
                                      SIGNATURE_ALGORITHM.get().createPublicKey(id))),
                          new EnrField(EnrField.IP_V4, addressBytes),
                          new EnrField(EnrField.TCP, listeningPort),
                          new EnrField(EnrField.UDP, discoveryPort),
                          new EnrField(
                              forkIdEnrField, Collections.singletonList(forkIdSupplier.get())));
                  nodeRecord.setSignature(
                      nodeKey
                          .sign(Hash.keccak256(nodeRecord.serializeNoSignature()))
                          .encodedBytes()
                          .slice(0, 64));

                  LOG.info("Writing node record to disk. {}", nodeRecord);
                  final KeyValueStorageTransaction keyValueStorageTransaction =
                      keyValueStorage.startTransaction();
                  keyValueStorageTransaction.put(
                      Bytes.wrap(SEQ_NO_STORE_KEY.getBytes(UTF_8)).toArray(),
                      nodeRecord.serialize().toArray());
                  keyValueStorageTransaction.commit();
                  return nodeRecord;
                });
    localNode
        .orElseThrow(() -> new IllegalStateException("Local node should be set here"))
        .setNodeRecord(newNodeRecord);
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
    final int udpPort = sourceEndpoint.getUdpPort();
    final int tcpPort =
        packet
            .getPacketData(PingPacketData.class)
            .flatMap(PingPacketData::getFrom)
            .flatMap(Endpoint::getTcpPort)
            .orElse(udpPort);

    // Notify the peer controller.
    final String host = sourceEndpoint.getHost();
    final DiscoveryPeer peer =
        DiscoveryPeer.fromEnode(
            EnodeURLImpl.builder()
                .nodeId(packet.getNodeId())
                .ipAddress(host)
                .listeningPort(tcpPort)
                .discoveryPort(udpPort)
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
                handleOutgoingPacketError(err, peer, packet);
                return;
              }
              peer.setLastContacted(System.currentTimeMillis());
            });
  }

  protected abstract void handleOutgoingPacketError(
      final Throwable err, final DiscoveryPeer peer, final Packet packet);

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
   * Removes a previously added peer bonded observer.
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

  public void bond(final Peer peer) {
    controller.ifPresent(
        c -> {
          DiscoveryPeer.from(peer).ifPresent(c::handleBondingRequest);
        });
  }

  @VisibleForTesting
  public Optional<DiscoveryPeer> getLocalNode() {
    return localNode;
  }
}
