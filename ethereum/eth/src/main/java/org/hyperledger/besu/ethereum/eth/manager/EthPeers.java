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
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  private final Map<PeerConnection, EthPeer> connections = new ConcurrentHashMap<>();
  private final String protocolName;
  private final Clock clock;
  private final List<NodeMessagePermissioningProvider> permissioningProviders;
  private final int maxPeers;
  private final int maxMessageSize;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = Subscribers.create();
  private final Collection<PendingPeerRequest> pendingRequests = new CopyOnWriteArrayList<>();

  private Comparator<EthPeer> bestPeerComparator;

  public EthPeers(
      final String protocolName,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final int maxPeers,
      final int maxMessageSize) {
    this(protocolName, clock, metricsSystem, maxPeers, maxMessageSize, Collections.emptyList());
  }

  public EthPeers(
      final String protocolName,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final int maxPeers,
      final int maxMessageSize,
      final List<NodeMessagePermissioningProvider> permissioningProviders) {
    this.protocolName = protocolName;
    this.clock = clock;
    this.permissioningProviders = permissioningProviders;
    this.maxPeers = maxPeers;
    this.maxMessageSize = maxMessageSize;
    this.bestPeerComparator = HEAVIEST_CHAIN;
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.PEERS,
        "pending_peer_requests_current",
        "Number of peer requests currently pending because peers are busy",
        pendingRequests::size);
  }

  public void registerConnection(
      final PeerConnection peerConnection, final List<PeerValidator> peerValidators) {
    final EthPeer peer =
        new EthPeer(
            peerConnection,
            protocolName,
            this::invokeConnectionCallbacks,
            peerValidators,
            maxMessageSize,
            clock,
            permissioningProviders);
    final EthPeer ethPeer = connections.putIfAbsent(peerConnection, peer);
    LOG.debug(
        "Adding new EthPeer {} {}",
        peer.getShortNodeId(),
        ethPeer == null ? "for the first time" : "");
  }

  public void registerDisconnect(final PeerConnection connection) {
    final EthPeer peer = connections.remove(connection);
    if (peer != null) {
      disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
      peer.handleDisconnect();
      abortPendingRequestsAssignedToDisconnectedPeers();
      LOG.debug("Disconnected EthPeer {}", peer);
    }
    reattemptPendingPeerRequests();
  }

  private void abortPendingRequestsAssignedToDisconnectedPeers() {
    synchronized (this) {
      final Iterator<PendingPeerRequest> iterator = pendingRequests.iterator();
      while (iterator.hasNext()) {
        final PendingPeerRequest request = iterator.next();
        if (request.getAssignedPeer().map(EthPeer::isDisconnected).orElse(false)) {
          request.abort();
        }
      }
    }
  }

  public EthPeer peer(final PeerConnection peerConnection) {
    return connections.get(peerConnection);
  }

  public PendingPeerRequest executePeerRequest(
      final PeerRequest request, final long minimumBlockNumber, final Optional<EthPeer> peer) {
    final PendingPeerRequest pendingPeerRequest =
        new PendingPeerRequest(this, request, minimumBlockNumber, peer);
    synchronized (this) {
      if (!pendingPeerRequest.attemptExecution()) {
        pendingRequests.add(pendingPeerRequest);
      }
    }
    return pendingPeerRequest;
  }

  public void dispatchMessage(
      final EthPeer peer, final EthMessage ethMessage, final String protocolName) {
    peer.dispatch(ethMessage, protocolName);
    if (peer.hasAvailableRequestCapacity()) {
      reattemptPendingPeerRequests();
    }
  }

  public void dispatchMessage(final EthPeer peer, final EthMessage ethMessage) {
    dispatchMessage(peer, ethMessage, protocolName);
  }

  private void reattemptPendingPeerRequests() {
    synchronized (this) {
      pendingRequests.removeIf(PendingPeerRequest::attemptExecution);
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
    return connections.size();
  }

  public int getMaxPeers() {
    return maxPeers;
  }

  public Stream<EthPeer> streamAllPeers() {
    return connections.values().stream();
  }

  public Stream<EthPeer> streamAvailablePeers() {
    return streamAllPeers().filter(EthPeer::readyForRequests);
  }

  public Stream<EthPeer> streamBestPeers() {
    return streamAvailablePeers().sorted(getBestChainComparator().reversed());
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

  @FunctionalInterface
  public interface ConnectCallback {
    void onPeerConnected(EthPeer newPeer);
  }

  @Override
  public String toString() {
    if (connections.isEmpty()) {
      return "0 EthPeers {}";
    }
    final String connectionsList =
        connections.values().stream()
            .sorted()
            .map(EthPeer::toString)
            .collect(Collectors.joining(", \n"));
    return connections.size() + " EthPeers {\n" + connectionsList + '}';
  }

  private void invokeConnectionCallbacks(final EthPeer peer) {
    connectCallbacks.forEach(cb -> cb.onPeerConnected(peer));
  }
}
