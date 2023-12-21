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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer.DisconnectCallback;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthPeers {
  private static final Logger LOG = LoggerFactory.getLogger(EthPeers.class);
  public static final Comparator<EthPeer> TOTAL_DIFFICULTY =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedTotalDifficulty()));

  public static final Comparator<EthPeer> CHAIN_HEIGHT =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedHeight()));

  public static final Comparator<EthPeer> HEAVIEST_CHAIN =
      TOTAL_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  public static final Comparator<EthPeer> LEAST_TO_MOST_BUSY =
      Comparator.comparing(EthPeer::outstandingRequests)
          .thenComparing(EthPeer::getLastRequestTimestamp);
  public static final int NODE_ID_LENGTH = 64;
  public static final int USEFULL_PEER_SCORE_THRESHOLD = 102;

  private final Map<Bytes, EthPeer> completeConnections = new ConcurrentHashMap<>();

  private final Cache<PeerConnection, EthPeer> incompleteConnections =
      CacheBuilder.newBuilder()
          .expireAfterWrite(Duration.ofSeconds(20L))
          .concurrencyLevel(1)
          .removalListener(this::onCacheRemoval)
          .build();
  private final String protocolName;
  private final Clock clock;
  private final List<NodeMessagePermissioningProvider> permissioningProviders;
  private final int maxMessageSize;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = Subscribers.create();
  private final Collection<PendingPeerRequest> pendingRequests = new CopyOnWriteArrayList<>();
  private final int peerLowerBound;
  private final int peerUpperBound;
  private final int maxRemotelyInitiatedConnections;
  private final Boolean randomPeerPriority;
  private final Bytes nodeIdMask = Bytes.random(NODE_ID_LENGTH);
  private final Supplier<ProtocolSpec> currentProtocolSpecSupplier;

  private Comparator<EthPeer> bestPeerComparator;
  private final Bytes localNodeId;
  private RlpxAgent rlpxAgent;

  private final Counter connectedPeersCounter;

  public EthPeers(
      final String protocolName,
      final Supplier<ProtocolSpec> currentProtocolSpecSupplier,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final int maxMessageSize,
      final List<NodeMessagePermissioningProvider> permissioningProviders,
      final Bytes localNodeId,
      final int peerLowerBound,
      final int peerUpperBound,
      final int maxRemotelyInitiatedConnections,
      final Boolean randomPeerPriority) {
    this.protocolName = protocolName;
    this.currentProtocolSpecSupplier = currentProtocolSpecSupplier;
    this.clock = clock;
    this.permissioningProviders = permissioningProviders;
    this.maxMessageSize = maxMessageSize;
    this.bestPeerComparator = HEAVIEST_CHAIN;
    this.localNodeId = localNodeId;
    this.peerLowerBound = peerLowerBound;
    this.peerUpperBound = peerUpperBound;
    this.maxRemotelyInitiatedConnections = maxRemotelyInitiatedConnections;
    this.randomPeerPriority = randomPeerPriority;
    LOG.trace(
        "MaxPeers: {}, Lower Bound: {}, Max Remote: {}",
        peerUpperBound,
        peerLowerBound,
        maxRemotelyInitiatedConnections);
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.ETHEREUM,
        "peer_count",
        "The current number of peers connected",
        () -> (int) streamAvailablePeers().filter(p -> p.readyForRequests()).count());
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.PEERS,
        "pending_peer_requests_current",
        "Number of peer requests currently pending because peers are busy",
        pendingRequests::size);
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.ETHEREUM,
        "peer_limit",
        "The maximum number of peers this node allows to connect",
        () -> peerUpperBound);

    connectedPeersCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PEERS, "connected_total", "Total number of peers connected");
  }

  public void registerNewConnection(
      final PeerConnection newConnection, final List<PeerValidator> peerValidators) {
    final Bytes id = newConnection.getPeer().getId();
    synchronized (this) {
      EthPeer ethPeer = completeConnections.get(id);
      if (ethPeer == null) {
        final Optional<EthPeer> peerInList =
            incompleteConnections.asMap().values().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
        ethPeer =
            peerInList.orElse(
                new EthPeer(
                    newConnection,
                    protocolName,
                    this::ethPeerStatusExchanged,
                    peerValidators,
                    maxMessageSize,
                    clock,
                    permissioningProviders,
                    localNodeId));
      }
      incompleteConnections.put(newConnection, ethPeer);
    }
  }

  public int getPeerLowerBound() {
    return peerLowerBound;
  }

  @NotNull
  private List<PeerConnection> getIncompleteConnections(final Bytes id) {
    return incompleteConnections.asMap().keySet().stream()
        .filter(nrc -> nrc.getPeer().getId().equals(id))
        .collect(Collectors.toList());
  }

  public boolean registerDisconnect(final PeerConnection connection) {
    final EthPeer peer = peer(connection);
    return registerDisconnect(peer.getId(), peer, connection);
  }

  private boolean registerDisconnect(
      final Bytes id, final EthPeer peer, final PeerConnection connection) {
    incompleteConnections.invalidate(connection);
    boolean removed = false;
    if (peer != null && peer.getConnection().equals(connection)) {
      if (!peerHasIncompleteConnection(id)) {
        removed = completeConnections.remove(id, peer);
        disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
        peer.handleDisconnect();
        abortPendingRequestsAssignedToDisconnectedPeers();
        if (peer.getReputation().getScore() > USEFULL_PEER_SCORE_THRESHOLD) {
          LOG.debug("Disonnected USEFULL peer {}", peer);
        } else {
          LOG.debug("Disconnected EthPeer {}", peer.getShortNodeId());
        }
      }
    }
    reattemptPendingPeerRequests();
    return removed;
  }

  private boolean peerHasIncompleteConnection(final Bytes id) {
    return getIncompleteConnections(id).stream().anyMatch(conn -> !conn.isDisconnected());
  }

  private void abortPendingRequestsAssignedToDisconnectedPeers() {
    synchronized (this) {
      for (final PendingPeerRequest request : pendingRequests) {
        if (request.getAssignedPeer().map(EthPeer::isDisconnected).orElse(false)) {
          request.abort();
        }
      }
    }
  }

  public EthPeer peer(final PeerConnection connection) {
    final EthPeer ethPeer = incompleteConnections.getIfPresent(connection);
    return ethPeer != null ? ethPeer : completeConnections.get(connection.getPeer().getId());
  }

  public PendingPeerRequest executePeerRequest(
      final PeerRequest request, final long minimumBlockNumber, final Optional<EthPeer> peer) {
    final long actualMinBlockNumber;
    if (minimumBlockNumber > 0 && currentProtocolSpecSupplier.get().isPoS()) {
      // if on PoS do not enforce a min block number, since the estimated chain height of the remote
      // peer is not updated anymore.
      actualMinBlockNumber = 0;
    } else {
      actualMinBlockNumber = minimumBlockNumber;
    }
    final PendingPeerRequest pendingPeerRequest =
        new PendingPeerRequest(this, request, actualMinBlockNumber, peer);
    synchronized (this) {
      if (!pendingPeerRequest.attemptExecution()) {
        pendingRequests.add(pendingPeerRequest);
      }
    }
    return pendingPeerRequest;
  }

  public void dispatchMessage(
      final EthPeer peer, final EthMessage ethMessage, final String protocolName) {
    final Optional<RequestManager> maybeRequestManager = peer.dispatch(ethMessage, protocolName);
    if (maybeRequestManager.isPresent() && peer.hasAvailableRequestCapacity()) {
      reattemptPendingPeerRequests();
    }
  }

  public void dispatchMessage(final EthPeer peer, final EthMessage ethMessage) {
    dispatchMessage(peer, ethMessage, protocolName);
  }

  @VisibleForTesting
  void reattemptPendingPeerRequests() {
    synchronized (this) {
      final List<EthPeer> peers = streamAvailablePeers().collect(Collectors.toList());
      final Iterator<PendingPeerRequest> iterator = pendingRequests.iterator();
      while (iterator.hasNext() && peers.stream().anyMatch(EthPeer::hasAvailableRequestCapacity)) {
        final PendingPeerRequest request = iterator.next();
        if (request.attemptExecution()) {
          pendingRequests.remove(request);
        }
      }
    }
  }

  public long subscribeConnect(final ConnectCallback callback) {
    return connectCallbacks.subscribe(callback);
  }

  public void unsubscribeConnect(final long id) {
    connectCallbacks.unsubscribe(id);
  }

  public void subscribeDisconnect(final DisconnectCallback callback) {
    disconnectCallbacks.subscribe(callback);
  }

  public int peerCount() {
    removeDisconnectedPeers();
    return completeConnections.size();
  }

  public int getMaxPeers() {
    return peerUpperBound;
  }

  public Stream<EthPeer> streamAllPeers() {
    return completeConnections.values().stream();
  }

  private void removeDisconnectedPeers() {
    completeConnections
        .values()
        .forEach(
            ep -> {
              if (ep.isDisconnected()) {
                registerDisconnect(ep.getId(), ep, ep.getConnection());
              }
            });
  }

  public Stream<EthPeer> streamAvailablePeers() {
    return streamAllPeers()
        .filter(EthPeer::readyForRequests)
        .filter(peer -> !peer.isDisconnected());
  }

  public Stream<EthPeer> streamBestPeers() {
    return streamAvailablePeers()
        .filter(EthPeer::isFullyValidated)
        .sorted(getBestChainComparator().reversed());
  }

  public Optional<EthPeer> bestPeer() {
    return streamAvailablePeers().max(getBestChainComparator());
  }

  public Optional<EthPeer> bestPeerWithHeightEstimate() {
    return bestPeerMatchingCriteria(
        p -> p.isFullyValidated() && p.chainState().hasEstimatedHeight());
  }

  public Optional<EthPeer> bestPeerMatchingCriteria(final Predicate<EthPeer> matchesCriteria) {
    return streamAvailablePeers().filter(matchesCriteria).max(getBestChainComparator());
  }

  public void setBestChainComparator(final Comparator<EthPeer> comparator) {
    LOG.info("Updating the default best peer comparator");
    bestPeerComparator = comparator;
  }

  public Comparator<EthPeer> getBestChainComparator() {
    return bestPeerComparator;
  }

  public void setRlpxAgent(final RlpxAgent rlpxAgent) {
    this.rlpxAgent = rlpxAgent;
  }

  public Stream<PeerConnection> getAllActiveConnections() {
    return completeConnections.values().stream()
        .map(EthPeer::getConnection)
        .filter(c -> !c.isDisconnected());
  }

  public Stream<PeerConnection> getAllConnections() {
    return Stream.concat(
            completeConnections.values().stream().map(EthPeer::getConnection),
            incompleteConnections.asMap().keySet().stream())
        .distinct()
        .filter(c -> !c.isDisconnected());
  }

  public boolean shouldConnect(final Peer peer, final boolean inbound) {
    final Bytes id = peer.getId();
    if (peerCount() >= peerUpperBound && !canExceedPeerLimits(id)) {
      return false;
    }
    final EthPeer ethPeer = completeConnections.get(id);
    if (ethPeer != null && !ethPeer.isDisconnected()) {
      return false;
    }
    final List<PeerConnection> incompleteConnections = getIncompleteConnections(id);
    if (!incompleteConnections.isEmpty()) {
      if (incompleteConnections.stream()
          .anyMatch(c -> !c.isDisconnected() && (!inbound || (inbound && c.inboundInitiated())))) {
        return false;
      }
    }
    return true;
  }

  public void disconnectWorstUselessPeer() {
    streamAvailablePeers()
        .sorted(getBestChainComparator())
        .findFirst()
        .ifPresent(
            peer -> {
              LOG.atDebug()
                  .setMessage(
                      "disconnecting peer {}. Waiting for better peers. Current {} of max {}")
                  .addArgument(peer::getShortNodeId)
                  .addArgument(this::peerCount)
                  .addArgument(this::getMaxPeers)
                  .log();
              peer.disconnect(DisconnectMessage.DisconnectReason.USELESS_PEER);
            });
  }

  @FunctionalInterface
  public interface ConnectCallback {
    void onPeerConnected(EthPeer newPeer);
  }

  @Override
  public String toString() {
    if (completeConnections.isEmpty()) {
      return "0 EthPeers {}";
    }
    final String connectionsList =
        completeConnections.values().stream()
            .sorted()
            .map(EthPeer::toString)
            .collect(Collectors.joining(", \n"));
    return completeConnections.size() + " EthPeers {\n" + connectionsList + '}';
  }

  private void ethPeerStatusExchanged(final EthPeer peer) {
    if (addPeerToEthPeers(peer)) {
      connectedPeersCounter.inc();
      connectCallbacks.forEach(cb -> cb.onPeerConnected(peer));
    }
  }

  private int comparePeerPriorities(final EthPeer p1, final EthPeer p2) {
    final PeerConnection a = p1.getConnection();
    final PeerConnection b = p2.getConnection();
    final boolean aCanExceedPeerLimits = canExceedPeerLimits(a.getPeer().getId());
    final boolean bCanExceedPeerLimits = canExceedPeerLimits(b.getPeer().getId());
    if (aCanExceedPeerLimits && !bCanExceedPeerLimits) {
      return -1;
    } else if (bCanExceedPeerLimits && !aCanExceedPeerLimits) {
      return 1;
    } else {
      return randomPeerPriority
          ? compareByMaskedNodeId(a, b)
          : compareConnectionInitiationTimes(a, b);
    }
  }

  private boolean canExceedPeerLimits(final Bytes peerId) {
    if (rlpxAgent == null) {
      return false;
    }
    return rlpxAgent.canExceedConnectionLimits(peerId);
  }

  private int compareConnectionInitiationTimes(final PeerConnection a, final PeerConnection b) {
    return Math.toIntExact(a.getInitiatedAt() - b.getInitiatedAt());
  }

  private int compareByMaskedNodeId(final PeerConnection a, final PeerConnection b) {
    return a.getPeer().getId().xor(nodeIdMask).compareTo(b.getPeer().getId().xor(nodeIdMask));
  }

  private void enforceRemoteConnectionLimits() {
    if (!shouldLimitRemoteConnections() || peerCount() < maxRemotelyInitiatedConnections) {
      // Nothing to do
      return;
    }

    getActivePrioritizedPeers()
        .filter(p -> p.getConnection().inboundInitiated())
        .filter(p -> !canExceedPeerLimits(p.getId()))
        .skip(maxRemotelyInitiatedConnections)
        .forEach(
            conn -> {
              LOG.trace(
                  "Too many remotely initiated connections. Disconnect low-priority connection: {}, maxRemote={}",
                  conn,
                  maxRemotelyInitiatedConnections);
              conn.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS);
            });
  }

  private Stream<EthPeer> getActivePrioritizedPeers() {
    return completeConnections.values().stream()
        .filter(p -> !p.isDisconnected())
        .sorted(this::comparePeerPriorities);
  }

  private void enforceConnectionLimits() {
    if (peerCount() < peerUpperBound) {
      // Nothing to do - we're under our limits
      return;
    }
    getActivePrioritizedPeers()
        .skip(peerUpperBound)
        .map(EthPeer::getConnection)
        .filter(c -> !canExceedPeerLimits(c.getPeer().getId()))
        .forEach(
            conn -> {
              LOG.trace(
                  "Too many connections. Disconnect low-priority connection: {}, maxConnections={}",
                  conn,
                  peerUpperBound);
              conn.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS);
            });
  }

  private boolean remoteConnectionLimitReached() {
    return shouldLimitRemoteConnections()
        && countUntrustedRemotelyInitiatedConnections() >= maxRemotelyInitiatedConnections;
  }

  private boolean shouldLimitRemoteConnections() {
    return maxRemotelyInitiatedConnections < peerUpperBound;
  }

  private long countUntrustedRemotelyInitiatedConnections() {
    return completeConnections.values().stream()
        .map(ep -> ep.getConnection())
        .filter(c -> c.inboundInitiated())
        .filter(c -> !c.isDisconnected())
        .filter(conn -> !canExceedPeerLimits(conn.getPeer().getId()))
        .count();
  }

  private void onCacheRemoval(
      final RemovalNotification<PeerConnection, EthPeer> removalNotification) {
    if (removalNotification.wasEvicted()) {
      final PeerConnection peerConnectionRemoved = removalNotification.getKey();
      final PeerConnection peerConnectionOfEthPeer = removalNotification.getValue().getConnection();
      if (!peerConnectionRemoved.equals(peerConnectionOfEthPeer)) {
        // If this connection is not the connection of the EthPeer by now we can disconnect
        peerConnectionRemoved.disconnect(DisconnectMessage.DisconnectReason.ALREADY_CONNECTED);
      }
    }
  }

  private boolean addPeerToEthPeers(final EthPeer peer) {
    // We have a connection to a peer that is on the right chain and is willing to connect to us.
    // Figure out whether we want to keep this peer and add it to the EthPeers connections.
    if (completeConnections.containsValue(peer)) {
      return false;
    }
    final PeerConnection connection = peer.getConnection();
    final Bytes id = peer.getId();
    if (!randomPeerPriority) {
      // Disconnect if too many peers
      if (!canExceedPeerLimits(id) && peerCount() >= peerUpperBound) {
        LOG.trace(
            "Too many peers. Disconnect connection: {}, max connections {}",
            connection,
            peerUpperBound);
        connection.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS);
        return false;
      }
      // Disconnect if too many remotely-initiated connections
      if (connection.inboundInitiated()
          && !canExceedPeerLimits(id)
          && remoteConnectionLimitReached()) {
        LOG.trace(
            "Too many remotely-initiated connections. Disconnect incoming connection: {}, maxRemote={}",
            connection,
            maxRemotelyInitiatedConnections);
        connection.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS);
        return false;
      }
      final boolean added = (completeConnections.putIfAbsent(id, peer) == null);
      if (added) {
        LOG.trace("Added peer {} with connection {} to completeConnections", id, connection);
      } else {
        LOG.trace("Did not add peer {} with connection {} to completeConnections", id, connection);
      }
      return added;
    } else {
      // randomPeerPriority! Add the peer and if there are too many connections fix it
      completeConnections.putIfAbsent(id, peer);
      enforceRemoteConnectionLimits();
      enforceConnectionLimits();
      return completeConnections.containsKey(id);
    }
  }
}
