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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public class EthPeers {
  public static final Comparator<EthPeer> TOTAL_DIFFICULTY =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedTotalDifficulty()));

  public static final Comparator<EthPeer> CHAIN_HEIGHT =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedHeight()));

  public static final Comparator<EthPeer> BEST_CHAIN = TOTAL_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  public static final Comparator<EthPeer> LEAST_TO_MOST_BUSY =
      Comparator.comparing(EthPeer::outstandingRequests)
          .thenComparing(EthPeer::getLastRequestTimestamp);

  private final Map<Bytes, EthPeer> connections = new ConcurrentHashMap<>();
  private final Map<PeerConnection, EthPeer> preStatusExchangedPeers = new ConcurrentHashMap<>();
  private final String protocolName;
  private final Clock clock;
  private final List<NodeMessagePermissioningProvider> permissioningProviders;
  private final int maxPeers;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = Subscribers.create();
  private final Collection<PendingPeerRequest> pendingRequests = new CopyOnWriteArrayList<>();

  public EthPeers(
      final String protocolName,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final int maxPeers) {
    this(protocolName, clock, metricsSystem, maxPeers, Collections.emptyList());
  }

  public EthPeers(
      final String protocolName,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final int maxPeers,
      final List<NodeMessagePermissioningProvider> permissioningProviders) {
    this.protocolName = protocolName;
    this.clock = clock;
    this.permissioningProviders = permissioningProviders;
    this.maxPeers = maxPeers;
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.PEERS,
        "pending_peer_requests_current",
        "Number of peer requests currently pending because peers are busy",
        pendingRequests::size);
  }

  public void registerConnection(final PeerConnection peerConnection) {
    connections.compute(
        peerConnection.getPeer().getId(),
        (con, existingPeer) -> {
          if (existingPeer != null) {
            existingPeer.replaceConnection(peerConnection);
            return existingPeer;
          } else {
            return preStatusExchangedPeers.get(peerConnection);
          }
        });
    preStatusExchangedPeers.remove(
        peerConnection); // TODO: should we have a time limit that peers can be in the preStatus
    // list?
  }

  public EthPeer preStatusExchangedConnection(
      final PeerConnection peerConnection, final List<PeerValidator> peerValidators) {
    final CompletableFuture<EthPeer> ethPeerCompletableFuture = new CompletableFuture<>();
    final EthPeer peer =
        new EthPeer(
            peerConnection,
            protocolName,
            this::invokeConnectionCallbacks,
            peerValidators,
            clock,
            permissioningProviders,
            ethPeerCompletableFuture);
    ethPeerCompletableFuture.whenComplete(
        (peerToAdd, execption) -> {
          if (execption == null) {
            connections.put(
                peerConnection.getPeer().getId(),
                peer); // TODO: add getter for id to EthPeer. I think it is a bit confusing that
            // EthPeer is not implementing Peer ...
          } else {
            // TODO: do we have to call disconnect on the connection here? Maybe only if that was a
            // TimeoutException?
          }
          preStatusExchangedPeers.remove(peerConnection.getPeer().getId());
        });
    ethPeerCompletableFuture.orTimeout(
        5, TimeUnit.SECONDS); // if the connection is not ready after 5 seconds complete the future
    // exeptionally
    preStatusExchangedPeers.put(peerConnection, peer);
    return peer;
  }

  public void registerDisconnect(final PeerConnection connection) {
    final EthPeer peer = connections.remove(connection.getPeer().getId());
    if (peer != null) {
      disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
      peer.handleDisconnect();
      abortPendingRequestsAssignedToDisconnectedPeers();
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
    return connections.getOrDefault(
        peerConnection.getPeer().getId(), preStatusExchangedPeers.get(peerConnection));
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

  public Stream<EthPeer>
      streamAvailablePeers() { // TODO: remove this method, as all the conections in 'connections'
    // are ready!
    return streamAllPeers().filter(EthPeer::readyForRequests);
  }

  public Stream<EthPeer> streamBestPeers() {
    return streamAvailablePeers().sorted(BEST_CHAIN.reversed());
  }

  public Optional<EthPeer> bestPeer() {
    return streamAvailablePeers().max(BEST_CHAIN);
  }

  public Optional<EthPeer> bestPeerWithHeightEstimate() {
    return bestPeerMatchingCriteria(
        p -> p.isFullyValidated() && p.chainState().hasEstimatedHeight());
  }

  public Optional<EthPeer> bestPeerMatchingCriteria(final Predicate<EthPeer> matchesCriteria) {
    return streamAvailablePeers().filter(matchesCriteria).max(BEST_CHAIN);
  }

  @FunctionalInterface
  public interface ConnectCallback {
    void onPeerConnected(
        EthPeer newPeer); // TODO: We could make use of a "ready" callback instead of this ... ?
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
