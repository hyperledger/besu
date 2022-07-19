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
package org.hyperledger.besu.ethereum.p2p.network;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.discovery.VertxPeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeerPrivileges;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.LocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.MaintainedPeers;
import org.hyperledger.besu.ethereum.p2p.peers.MutableLocalNode;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerPrivileges;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsDenylist;
import org.hyperledger.besu.ethereum.p2p.rlpx.ConnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.DisconnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.MessageCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.devp2p.EthereumNodeRecord;
import org.apache.tuweni.discovery.DNSDaemon;
import org.apache.tuweni.discovery.DNSDaemonListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class DefaultP2PNetwork implements P2PNetwork {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultP2PNetwork.class);

  private final ScheduledExecutorService peerConnectionScheduler =
      Executors.newSingleThreadScheduledExecutor();
  private final PeerDiscoveryAgent peerDiscoveryAgent;
  private final RlpxAgent rlpxAgent;

  private final NetworkingConfiguration config;

  private final Bytes nodeId;
  private final MutableLocalNode localNode;

  private final PeerPermissions peerPermissions;
  private final MaintainedPeers maintainedPeers;

  private final NatService natService;

  private OptionalLong peerBondedObserverId = OptionalLong.empty();

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final CountDownLatch shutdownLatch = new CountDownLatch(2);
  private final Duration shutdownTimeout = Duration.ofMinutes(1);
  private DNSDaemon dnsDaemon;

  @VisibleForTesting final AtomicReference<List<DiscoveryPeer>> dnsPeers = new AtomicReference<>();

  /**
   * Creates a peer networking service for production purposes.
   *
   * <p>The caller is expected to provide the IP address to be advertised (normally this node's
   * public IP address), as well as TCP and UDP port numbers for the RLPx agent and the discovery
   * agent, respectively.
   *
   * @param localNode A representation of the local node
   * @param peerDiscoveryAgent The agent responsible for discovering peers on the network.
   * @param nodeKey The node key through which cryptographic operations can be performed
   * @param config The network configuration to use.
   * @param peerPermissions An object that determines whether peers are allowed to connect
   * @param natService The NAT environment manager.
   * @param maintainedPeers A collection of peers for which we are expected to maintain connections
   * @param reputationManager An object that inspect disconnections for misbehaving peers that can
   *     then be blacklisted.
   */
  DefaultP2PNetwork(
      final MutableLocalNode localNode,
      final PeerDiscoveryAgent peerDiscoveryAgent,
      final RlpxAgent rlpxAgent,
      final NodeKey nodeKey,
      final NetworkingConfiguration config,
      final PeerPermissions peerPermissions,
      final NatService natService,
      final MaintainedPeers maintainedPeers,
      final PeerDenylistManager reputationManager) {
    this.localNode = localNode;
    this.peerDiscoveryAgent = peerDiscoveryAgent;
    this.rlpxAgent = rlpxAgent;
    this.config = config;
    this.natService = natService;
    this.maintainedPeers = maintainedPeers;

    this.nodeId = nodeKey.getPublicKey().getEncodedBytes();
    this.peerPermissions = peerPermissions;

    final int maxPeers = config.getRlpx().getMaxPeers();
    peerDiscoveryAgent.addPeerRequirement(() -> rlpxAgent.getConnectionCount() >= maxPeers);
    subscribeDisconnect(reputationManager);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      LOG.warn("Attempted to start an already started " + getClass().getSimpleName());
      return;
    }

    if (config.getDiscovery().isDiscoveryV5Enabled()) {
      LOG.warn("Discovery Protocol v5 is not available");
    }

    final String address = config.getDiscovery().getAdvertisedHost();
    final int configuredDiscoveryPort = config.getDiscovery().getBindPort();
    final int configuredRlpxPort = config.getRlpx().getBindPort();

    Optional.ofNullable(config.getDiscovery().getDNSDiscoveryURL())
        .ifPresent(
            disco -> {
              LOG.info("Starting DNS discovery with URL {}", disco);
              config
                  .getDnsDiscoveryServerOverride()
                  .ifPresent(
                      dnsServer ->
                          LOG.info(
                              "Starting DNS discovery with DNS Server override {}", dnsServer));

              dnsDaemon =
                  new DNSDaemon(
                      disco,
                      createDaemonListener(),
                      0L,
                      60000L,
                      config.getDnsDiscoveryServerOverride().orElse(null));
              dnsDaemon.start();
            });

    final int listeningPort = rlpxAgent.start().join();
    final int discoveryPort =
        peerDiscoveryAgent
            .start(
                (configuredDiscoveryPort == 0 && configuredRlpxPort == 0)
                    ? listeningPort
                    : configuredDiscoveryPort)
            .join();

    final Consumer<? super NatManager> natAction =
        natManager -> {
          final UpnpNatManager upnpNatManager = (UpnpNatManager) natManager;
          upnpNatManager.requestPortForward(
              discoveryPort, NetworkProtocol.UDP, NatServiceType.DISCOVERY);
          upnpNatManager.requestPortForward(
              listeningPort, NetworkProtocol.TCP, NatServiceType.RLPX);
        };

    natService.ifNatEnvironment(NatMethod.UPNP, natAction);
    natService.ifNatEnvironment(NatMethod.UPNPP2PONLY, natAction);

    setLocalNode(address, listeningPort, discoveryPort);

    peerBondedObserverId =
        OptionalLong.of(peerDiscoveryAgent.observePeerBondedEvents(this::handlePeerBondedEvent));

    // Periodically check maintained connections
    final int checkMaintainedConnectionsSec = config.getCheckMaintainedConnectionsFrequencySec();
    peerConnectionScheduler.scheduleWithFixedDelay(
        this::checkMaintainedConnectionPeers, 2, checkMaintainedConnectionsSec, TimeUnit.SECONDS);
    // Periodically initiate outgoing connections to discovered peers
    final int checkConnectionsSec = config.getInitiateConnectionsFrequencySec();
    peerConnectionScheduler.scheduleWithFixedDelay(
        this::attemptPeerConnections, checkConnectionsSec, checkConnectionsSec, TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    if (!this.started.get() || !stopped.compareAndSet(false, true)) {
      // We haven't started, or we've started and stopped already
      return;
    }

    getDnsDaemon().ifPresent(DNSDaemon::close);

    peerConnectionScheduler.shutdownNow();
    peerDiscoveryAgent.stop().whenComplete((res, err) -> shutdownLatch.countDown());
    rlpxAgent.stop().whenComplete((res, err) -> shutdownLatch.countDown());
    peerBondedObserverId.ifPresent(peerDiscoveryAgent::removePeerBondedObserver);
    peerBondedObserverId = OptionalLong.empty();
    peerPermissions.close();
  }

  @Override
  public void awaitStop() {
    try {
      if (!peerConnectionScheduler.awaitTermination(
          shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
        LOG.error(
            "{} did not shutdown cleanly: peerConnectionScheduler executor did not fully terminate.",
            this.getClass().getSimpleName());
      }
      if (!shutdownLatch.await(shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
        LOG.error(
            "{} did not shutdown cleanly: some internal services failed to fully terminate.",
            this.getClass().getSimpleName());
      }
    } catch (final InterruptedException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public boolean addMaintainedConnectionPeer(final Peer peer) {
    if (localNode.isReady()
        && localNode.getPeer() != null
        && localNode.getPeer().getEnodeURL() != null
        && peer.getEnodeURL().getNodeId().equals(localNode.getPeer().getEnodeURL().getNodeId())) {
      return false;
    }
    final boolean wasAdded = maintainedPeers.add(peer);
    peerDiscoveryAgent.bond(peer);
    rlpxAgent.connect(peer);
    return wasAdded;
  }

  @Override
  public boolean removeMaintainedConnectionPeer(final Peer peer) {
    final boolean wasRemoved = maintainedPeers.remove(peer);
    peerDiscoveryAgent.dropPeer(peer);
    LOG.debug("Disconnect requested for peer {}.", peer);
    rlpxAgent.disconnect(peer.getId(), DisconnectReason.REQUESTED);
    return wasRemoved;
  }

  @VisibleForTesting
  Optional<DNSDaemon> getDnsDaemon() {
    return Optional.ofNullable(dnsDaemon);
  }

  @VisibleForTesting
  DNSDaemonListener createDaemonListener() {
    return (seq, records) -> {
      final List<DiscoveryPeer> peers = new ArrayList<>();
      for (final EthereumNodeRecord enr : records) {
        final EnodeURL enodeURL =
            EnodeURLImpl.builder()
                .ipAddress(enr.ip())
                .nodeId(enr.publicKey().bytes())
                .discoveryPort(Optional.ofNullable(enr.udp()))
                .listeningPort(Optional.ofNullable(enr.tcp()))
                .build();
        final DiscoveryPeer peer = DiscoveryPeer.fromEnode(enodeURL);
        peers.add(peer);
        rlpxAgent.connect(peer);
      }
      // only replace dnsPeers if the lookup was successful:
      if (!peers.isEmpty()) {
        dnsPeers.set(peers);
      }
    };
  }

  @VisibleForTesting
  void checkMaintainedConnectionPeers() {
    if (!localNode.isReady()) {
      return;
    }
    maintainedPeers
        .streamPeers()
        .filter(p -> !rlpxAgent.getPeerConnection(p).isPresent())
        .forEach(rlpxAgent::connect);
  }

  @VisibleForTesting
  void attemptPeerConnections() {
    LOG.trace("Initiating connections to discovered peers.");
    rlpxAgent.connect(
        streamDiscoveredPeers()
            .filter(peer -> peer.getStatus() == PeerDiscoveryStatus.BONDED)
            .sorted(Comparator.comparing(DiscoveryPeer::getLastAttemptedConnection)));
  }

  @Override
  public Collection<PeerConnection> getPeers() {
    return rlpxAgent.streamConnections().collect(Collectors.toList());
  }

  @Override
  public Stream<DiscoveryPeer> streamDiscoveredPeers() {
    final List<DiscoveryPeer> peers = dnsPeers.get();
    if (peers != null) {
      Collections.shuffle(peers);
      return Stream.concat(peerDiscoveryAgent.streamDiscoveredPeers(), peers.stream());
    }
    return peerDiscoveryAgent.streamDiscoveredPeers();
  }

  @Override
  public CompletableFuture<PeerConnection> connect(final Peer peer) {
    return rlpxAgent.connect(peer);
  }

  @Override
  public void subscribe(final Capability capability, final MessageCallback callback) {
    rlpxAgent.subscribeMessage(capability, callback);
  }

  @Override
  public void subscribeConnect(final ConnectCallback callback) {
    rlpxAgent.subscribeConnect(callback);
  }

  @Override
  public void subscribeDisconnect(final DisconnectCallback callback) {
    rlpxAgent.subscribeDisconnect(callback);
  }

  private void handlePeerBondedEvent(final PeerBondedEvent peerBondedEvent) {
    rlpxAgent.connect(peerBondedEvent.getPeer());
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public boolean isListening() {
    return localNode.isReady();
  }

  @Override
  public boolean isP2pEnabled() {
    return true;
  }

  @Override
  public boolean isDiscoveryEnabled() {
    return peerDiscoveryAgent.isActive();
  }

  @Override
  public Optional<EnodeURL> getLocalEnode() {
    if (!localNode.isReady()) {
      return Optional.empty();
    }
    return Optional.of(localNode.getPeer().getEnodeURL());
  }

  private void setLocalNode(
      final String address, final int listeningPort, final int discoveryPort) {
    if (localNode.isReady()) {
      // Already set
      return;
    }

    // override advertised host if we detect an external IP address via NAT manager
    final String advertisedAddress = natService.queryExternalIPAddress(address);

    final EnodeURL localEnode =
        EnodeURLImpl.builder()
            .nodeId(nodeId)
            .ipAddress(advertisedAddress)
            .listeningPort(listeningPort)
            .discoveryPort(discoveryPort)
            .build();

    LOG.info("Enode URL {}", localEnode.toString());
    LOG.info("Node address {}", Util.publicKeyToAddress(localEnode.getNodeId()));
    localNode.setEnode(localEnode);
  }

  @Override
  public void updateNodeRecord() {
    peerDiscoveryAgent.updateNodeRecord();
  }

  public static class Builder {

    private Vertx vertx;
    private PeerDiscoveryAgent peerDiscoveryAgent;
    private RlpxAgent rlpxAgent;

    private NetworkingConfiguration config = NetworkingConfiguration.create();
    private List<Capability> supportedCapabilities;
    private NodeKey nodeKey;

    private MaintainedPeers maintainedPeers = new MaintainedPeers();
    private PeerPermissions peerPermissions = PeerPermissions.noop();

    private NatService natService = new NatService(Optional.empty());
    private boolean randomPeerPriority;

    private MetricsSystem metricsSystem;
    private StorageProvider storageProvider;
    private Supplier<List<Bytes>> forkIdSupplier;
    private Optional<TLSConfiguration> p2pTLSConfiguration = Optional.empty();

    public P2PNetwork build() {
      validate();
      return doBuild();
    }

    private P2PNetwork doBuild() {
      // Set up permissions
      // Fold peer reputation into permissions
      final PeerPermissionsDenylist misbehavingPeers = PeerPermissionsDenylist.create(500);
      final PeerDenylistManager reputationManager = new PeerDenylistManager(misbehavingPeers);
      peerPermissions = PeerPermissions.combine(peerPermissions, misbehavingPeers);

      final MutableLocalNode localNode =
          MutableLocalNode.create(config.getRlpx().getClientId(), 5, supportedCapabilities);
      final PeerPrivileges peerPrivileges = new DefaultPeerPrivileges(maintainedPeers);
      peerDiscoveryAgent = peerDiscoveryAgent == null ? createDiscoveryAgent() : peerDiscoveryAgent;
      rlpxAgent = rlpxAgent == null ? createRlpxAgent(localNode, peerPrivileges) : rlpxAgent;

      return new DefaultP2PNetwork(
          localNode,
          peerDiscoveryAgent,
          rlpxAgent,
          nodeKey,
          config,
          peerPermissions,
          natService,
          maintainedPeers,
          reputationManager);
    }

    private void validate() {
      checkState(nodeKey != null, "NodeKey must be set.");
      checkState(config != null, "NetworkingConfiguration must be set.");
      checkState(
          supportedCapabilities != null && supportedCapabilities.size() > 0,
          "Supported capabilities must be set and non-empty.");
      checkState(metricsSystem != null, "MetricsSystem must be set.");
      checkState(storageProvider != null, "StorageProvider must be set.");
      checkState(peerDiscoveryAgent != null || vertx != null, "Vertx must be set.");
      checkState(forkIdSupplier != null, "ForkIdSupplier must be set.");
    }

    private PeerDiscoveryAgent createDiscoveryAgent() {

      return new VertxPeerDiscoveryAgent(
          vertx,
          nodeKey,
          config.getDiscovery(),
          peerPermissions,
          natService,
          metricsSystem,
          storageProvider,
          forkIdSupplier);
    }

    private RlpxAgent createRlpxAgent(
        final LocalNode localNode, final PeerPrivileges peerPrivileges) {
      return RlpxAgent.builder()
          .nodeKey(nodeKey)
          .config(config.getRlpx())
          .peerPermissions(peerPermissions)
          .peerPrivileges(peerPrivileges)
          .localNode(localNode)
          .metricsSystem(metricsSystem)
          .randomPeerPriority(randomPeerPriority)
          .p2pTLSConfiguration(p2pTLSConfiguration)
          .build();
    }

    public Builder peerDiscoveryAgent(final PeerDiscoveryAgent peerDiscoveryAgent) {
      checkNotNull(peerDiscoveryAgent);
      this.peerDiscoveryAgent = peerDiscoveryAgent;
      return this;
    }

    public Builder rlpxAgent(final RlpxAgent rlpxAgent) {
      checkNotNull(rlpxAgent);
      this.rlpxAgent = rlpxAgent;
      return this;
    }

    public Builder randomPeerPriority(final boolean randomPeerPriority) {
      this.randomPeerPriority = randomPeerPriority;
      return this;
    }

    public Builder vertx(final Vertx vertx) {
      checkNotNull(vertx);
      this.vertx = vertx;
      return this;
    }

    public Builder nodeKey(final NodeKey nodeKey) {
      checkNotNull(nodeKey);
      this.nodeKey = nodeKey;
      return this;
    }

    public Builder config(final NetworkingConfiguration config) {
      checkNotNull(config);
      this.config = config;
      return this;
    }

    public Builder supportedCapabilities(final List<Capability> supportedCapabilities) {
      checkNotNull(supportedCapabilities);
      this.supportedCapabilities = supportedCapabilities;
      return this;
    }

    public Builder supportedCapabilities(final Capability... supportedCapabilities) {
      this.supportedCapabilities = Arrays.asList(supportedCapabilities);
      return this;
    }

    public Builder peerPermissions(final PeerPermissions peerPermissions) {
      checkNotNull(peerPermissions);
      this.peerPermissions = peerPermissions;
      return this;
    }

    public Builder metricsSystem(final MetricsSystem metricsSystem) {
      checkNotNull(metricsSystem);
      this.metricsSystem = metricsSystem;
      return this;
    }

    public Builder natService(final NatService natService) {
      checkNotNull(natService);
      this.natService = natService;
      return this;
    }

    public Builder maintainedPeers(final MaintainedPeers maintainedPeers) {
      checkNotNull(maintainedPeers);
      this.maintainedPeers = maintainedPeers;
      return this;
    }

    public Builder storageProvider(final StorageProvider storageProvider) {
      checkNotNull(storageProvider);
      this.storageProvider = storageProvider;
      return this;
    }

    public Builder forkIdSupplier(final Supplier<List<Bytes>> forkIdSupplier) {
      checkNotNull(forkIdSupplier);
      this.forkIdSupplier = forkIdSupplier;
      return this;
    }

    public Builder p2pTLSConfiguration(final Optional<TLSConfiguration> p2pTLSConfiguration) {
      checkNotNull(p2pTLSConfiguration);
      this.p2pTLSConfiguration = p2pTLSConfiguration;
      return this;
    }
  }
}
