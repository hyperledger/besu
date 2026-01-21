/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.discv5;

import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeerFactory;
import org.hyperledger.besu.ethereum.p2p.discovery.NodeRecordManager;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.ethereum.beacon.discovery.MutableDiscoverySystem;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DiscV5 implementation of {@link PeerDiscoveryAgent} that actively drives peer discovery and
 * outbound RLPx connection attempts.
 *
 * <p>This agent owns the lifecycle of the DiscV5 {@link MutableDiscoverySystem} and executes
 * periodic peer discovery using an adaptive cadence based on current peer connectivity.
 *
 * <p>Discovery cadence:
 *
 * <ul>
 *   <li>Fast (1 second) while the node is under-connected
 *   <li>Slow (30 seconds) once a sufficient number of peers has been reached
 * </ul>
 *
 * <p>Discovered peers are filtered for readiness, fork compatibility, and reachability before
 * connection attempts are delegated to the {@link RlpxAgent}.
 */
public final class PeerDiscoveryAgentV5 implements PeerDiscoveryAgent {

  private static final Logger LOG = LoggerFactory.getLogger(PeerDiscoveryAgentV5.class);

  /** Minimum ratio of connected peers required to switch to slow discovery cadence. */
  private static final double MINIMUM_PEER_RATIO = 0.8;

  public static final int DISCOVERY_TIMEOUT_SECONDS = 30;

  private final MutableDiscoverySystem discoverySystem;
  private final DiscoveryConfiguration discoveryConfig;
  private final ForkIdManager forkIdManager;
  private final NodeRecordManager nodeRecordManager;
  private final RlpxAgent rlpxAgent;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "discv5-peer-discovery"));

  // Indicates whether the discovery agent is running
  private volatile boolean running = false;
  // Indicates whether the discovery agent has been stopped
  private volatile boolean stopped = false;
  // Indicates whether a discovery operation is currently in progress
  private final AtomicBoolean discoveryInProgress = new AtomicBoolean(false);

  /**
   * Creates a new DiscV5 peer discovery agent.
   *
   * @param discoverySystem the underlying mutable DiscV5 discovery system
   * @param config the networking configuration
   * @param forkIdManager manager used to validate fork compatibility with peers
   * @param nodeRecordManager manager responsible for maintaining the local node record
   * @param rlpxAgent RLPx agent used to initiate outbound peer connections
   */
  public PeerDiscoveryAgentV5(
      final MutableDiscoverySystem discoverySystem,
      final NetworkingConfiguration config,
      final ForkIdManager forkIdManager,
      final NodeRecordManager nodeRecordManager,
      final RlpxAgent rlpxAgent) {

    this.discoverySystem =
        Objects.requireNonNull(discoverySystem, "discoverySystem must not be null");
    this.discoveryConfig = Objects.requireNonNull(config, "config must not be null").getDiscovery();
    this.forkIdManager = Objects.requireNonNull(forkIdManager, "forkIdManager must not be null");
    this.nodeRecordManager =
        Objects.requireNonNull(nodeRecordManager, "nodeRecordManager must not be null");
    this.rlpxAgent = Objects.requireNonNull(rlpxAgent, "rlpxAgent must not be null");
  }

  /**
   * Starts the DiscV5 discovery system and the adaptive discovery loop.
   *
   * @param tcpPort the local TCP port used for RLPx connections
   * @return a future completed with the TCP port once discovery has started
   */
  @Override
  public CompletableFuture<Integer> start(final int tcpPort) {
    if (!isEnabled()) {
      LOG.debug("DiscV5 peer discovery is disabled; not starting agent");
      return CompletableFuture.completedFuture(0);
    }
    LOG.info("Starting DiscV5 peer discovery agent on TCP port {}", tcpPort);
    running = true;
    scheduler.scheduleAtFixedRate(this::discoveryTick, 0, 1, TimeUnit.SECONDS);
    return discoverySystem
        .start()
        .thenApply(
            v -> {
              final int localPort =
                  discoverySystem.getLocalNodeRecord().getTcpAddress().orElseThrow().getPort();
              LOG.info("P2P DiscV5 peer discovery agent started and listening on {}", localPort);
              return localPort;
            })
        .whenComplete(
            (port, error) -> {
              if (error != null) {
                LOG.error("Failed to start DiscV5 peer discovery agent", error);
                running = false;
              }
            });
  }

  /**
   * Stops peer discovery, terminates scheduled tasks, and shuts down the discovery system.
   *
   * @return a completed future once shutdown has completed
   */
  @Override
  public CompletableFuture<?> stop() {
    LOG.info("Stopping DiscV5 Peer Discovery Agent");
    running = false;
    stopped = true;
    scheduler.shutdownNow();
    discoverySystem.stop();
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Updates the local node record in the discovery system.
   *
   * <p>This method is typically called when network parameters change that affect the node record
   * (e.g., advertised IP address or ports).
   */
  @Override
  public void updateNodeRecord() {
    if (!isEnabled()) {
      return;
    }
    nodeRecordManager.updateNodeRecord();
  }

  /**
   * Checks whether a discovered peer is compatible with the local fork ID.
   *
   * @param peer the discovered peer
   * @return {@code true} if the peer is fork-compatible or does not advertise a fork ID
   */
  @Override
  public boolean checkForkId(final DiscoveryPeer peer) {
    return peer.getForkId().map(forkIdManager::peerCheck).orElse(true);
  }

  /**
   * Streams peers discovered by the DiscV5 discovery system.
   *
   * @return a stream of discovered peers
   */
  @Override
  public Stream<DiscoveryPeer> streamDiscoveredPeers() {
    return discoverySystem.streamLiveNodes().map(DiscoveryPeerFactory::fromNodeRecord);
  }

  /**
   * Removes a peer from the discovery system.
   *
   * @param peerId the identifier of the peer to drop
   */
  @Override
  public void dropPeer(final PeerId peerId) {
    discoverySystem.deleteNodeRecord(peerId.getId());
  }

  /**
   * Indicates whether peer discovery is enabled via configuration.
   *
   * @return {@code true} if discovery is enabled
   */
  @Override
  public boolean isEnabled() {
    return discoveryConfig.isEnabled();
  }

  /**
   * Indicates whether the discovery agent has been stopped.
   *
   * @return {@code true} if the agent has been stopped
   */
  @Override
  public boolean isStopped() {
    return stopped;
  }

  /**
   * Adds a peer to the discovery system.
   *
   * @param peer the peer to add
   */
  @Override
  public void addPeer(final Peer peer) {
    peer.getNodeRecord().ifPresent(discoverySystem::addNodeRecord);
  }

  /**
   * Looks up a peer by its identifier.
   *
   * @param peerId the peer identifier
   * @return the peer if known to the discovery system
   */
  @Override
  public Optional<Peer> getPeer(final PeerId peerId) {
    return discoverySystem.lookupNode(peerId.getId()).map(DiscoveryPeerFactory::fromNodeRecord);
  }

  /** Determines whether the RLPx agent has reached a sufficient number of connected peers. */
  private boolean hasSufficientPeers() {
    return rlpxAgent.getConnectionCount() >= rlpxAgent.getMaxPeers() * MINIMUM_PEER_RATIO;
  }

  /** Periodic discovery task that enforces adaptive cadence and triggers peer discovery. */
  private void discoveryTick() {
    if (!running || hasSufficientPeers()) {
      return;
    }
    discoverAndConnect();
  }

  /** Executes a DiscV5 peer search and attempts outbound connections to suitable peers. */
  private void discoverAndConnect() {
    if (!discoveryInProgress.compareAndSet(false, true)) {
      return;
    }
    discoverySystem
        .searchForNewPeers()
        .orTimeout(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .whenComplete(
            (nodeRecords, error) -> {
              try {
                if (error != null) {
                  LOG.warn("DiscV5 peer discovery failed", error);
                  return;
                }
                candidatePeers(nodeRecords).forEach(rlpxAgent::connect);
              } finally {
                discoveryInProgress.set(false);
              }
            });
  }

  /** Builds a stream of candidate peers suitable for outbound connection attempts. */
  private Stream<DiscoveryPeer> candidatePeers(final Collection<NodeRecord> newPeers) {
    LOG.trace("Discovered {} new peers", newPeers.size());

    // Combine newly discovered peers with known peers and filter for suitability
    final Stream<NodeRecord> knownPeers = discoverySystem.streamLiveNodes();
    final List<DiscoveryPeer> candidates =
        Stream.concat(newPeers.stream(), knownPeers)
            .distinct()
            .map(DiscoveryPeerFactory::fromNodeRecord)
            .filter(DiscoveryPeer::isReadyForConnections)
            .filter(peer -> peer.getForkId().map(forkIdManager::peerCheck).orElse(true))
            .toList();
    LOG.trace("Total unique peers eligible for connection: {}", candidates.size());
    return candidates.stream();
  }
}
