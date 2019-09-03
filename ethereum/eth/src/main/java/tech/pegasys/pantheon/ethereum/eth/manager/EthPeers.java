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
package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.util.Subscribers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
        PantheonMetricCategory.PEERS,
        "pending_peer_requests_current",
        "Number of peer requests currently pending because peers are busy",
        pendingRequests::size);
  }

  void registerConnection(final PeerConnection peerConnection) {
    final EthPeer peer =
        new EthPeer(peerConnection, protocolName, this::invokeConnectionCallbacks, clock);
    connections.putIfAbsent(peerConnection, peer);
  }

  void registerDisconnect(final PeerConnection connection) {
    final EthPeer peer = connections.remove(connection);
    if (peer != null) {
      disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
      peer.handleDisconnect();
    }
    checkPendingConnections();
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
      checkPendingConnections();
    }
  }

  private void checkPendingConnections() {
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

  public Optional<EthPeer> bestPeer() {
    return streamAvailablePeers().max(BEST_CHAIN);
  }

  public Optional<EthPeer> bestPeerWithHeightEstimate() {
    return streamAvailablePeers().filter(p -> p.chainState().hasEstimatedHeight()).max(BEST_CHAIN);
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
