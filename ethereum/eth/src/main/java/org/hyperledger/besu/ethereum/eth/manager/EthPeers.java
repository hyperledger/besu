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
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EthPeers {
  public static final Comparator<EthPeer> TOTAL_DIFFICULTY =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedTotalDifficulty()));

  public static final Comparator<EthPeer> CHAIN_HEIGHT =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedHeight()));

  public static final Comparator<EthPeer> BEST_CHAIN = TOTAL_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  public static final Comparator<EthPeer> LEAST_TO_MOST_BUSY =
      Comparator.comparing(EthPeer::outstandingRequests)
          .thenComparing(EthPeer::getLastRequestTimestamp);

  private final Map<PeerConnection, EthPeer> connections = new ConcurrentHashMap<>();
  private final String protocolName;
  private final Clock clock;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = Subscribers.create();
  private final Collection<PendingPeerRequest> pendingRequests = new ArrayList<>();

  public EthPeers(final String protocolName, final Clock clock, final MetricsSystem metricsSystem) {
    this.protocolName = protocolName;
    this.clock = clock;
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.PEERS,
        "pending_peer_requests_current",
        "Number of peer requests currently pending because peers are busy",
        pendingRequests::size);
  }

  void registerConnection(
      final PeerConnection peerConnection, final List<PeerValidator> peerValidators) {
    final EthPeer peer =
        new EthPeer(
            peerConnection, protocolName, this::invokeConnectionCallbacks, peerValidators, clock);
    connections.putIfAbsent(peerConnection, peer);
  }

  void registerDisconnect(final PeerConnection connection) {
    final EthPeer peer = connections.remove(connection);
    if (peer != null) {
      disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
      peer.handleDisconnect();
      abortPendingRequestsAssignedToDisconnectedPeers();
    }
    reattemptPendingPeerRequests();
  }

  private void abortPendingRequestsAssignedToDisconnectedPeers() {
    synchronized (this) {
      pendingRequests.stream()
          .filter(
              pendingPeerRequest ->
                  pendingPeerRequest.getAssignedPeer().map(EthPeer::isDisconnected).orElse(false))
          .forEach(PendingPeerRequest::abort);
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

  public void dispatchMessage(final EthPeer peer, final EthMessage ethMessage) {
    peer.dispatch(ethMessage);
    if (peer.hasAvailableRequestCapacity()) {
      reattemptPendingPeerRequests();
    }
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

  public Stream<EthPeer> streamAvailablePeers() {
    return connections.values().stream().filter(EthPeer::readyForRequests);
  }

  public Stream<EthPeer> streamBestPeers() {
    return streamAvailablePeers().sorted(BEST_CHAIN.reversed());
  }

  public Optional<EthPeer> bestPeer() {
    return streamAvailablePeers().max(BEST_CHAIN);
  }

  public Optional<EthPeer> bestPeerWithHeightEstimate() {
    return bestPeerMatchingCriteria(p -> p.chainState().hasEstimatedHeight());
  }

  public Optional<EthPeer> bestPeerMatchingCriteria(final Predicate<EthPeer> matchesCriteria) {
    return streamAvailablePeers().filter(matchesCriteria::test).max(BEST_CHAIN);
  }

  @FunctionalInterface
  public interface ConnectCallback {
    void onPeerConnected(EthPeer newPeer);
  }

  @Override
  public String toString() {
    final String connectionsList =
        connections.values().stream().map(EthPeer::toString).collect(Collectors.joining(","));
    return "EthPeers{connections=" + connectionsList + '}';
  }

  private void invokeConnectionCallbacks(final EthPeer peer) {
    connectCallbacks.forEach(cb -> cb.onPeerConnected(peer));
  }
}
