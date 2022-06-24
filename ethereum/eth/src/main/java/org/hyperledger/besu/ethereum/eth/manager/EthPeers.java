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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthPeers {

  private static final Logger LOG = LoggerFactory.getLogger(EthPeers.class);

  public static final Comparator<EthPeer> TOTAL_DIFFICULTY =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedTotalDifficulty()));

  public static final Comparator<EthPeer> CHAIN_HEIGHT =
      Comparator.comparing(((final EthPeer p) -> p.chainState().getEstimatedHeight()));

  public static final Comparator<EthPeer> BEST_CHAIN = TOTAL_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  public static final Comparator<EthPeer> LEAST_TO_MOST_BUSY =
      Comparator.comparing(EthPeer::outstandingRequests)
          .thenComparing(EthPeer::getLastRequestTimestamp);

  private final Map<Bytes, EthPeer> connections = new ConcurrentHashMap<>();
  private final Map<PeerConnection, EthPeer> nonReadyConnections = new ConcurrentHashMap<>();
  private final String protocolName;
  private final Clock clock;
  private final List<NodeMessagePermissioningProvider> permissioningProviders;
  private final int maxPeers;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private final Subscribers<DisconnectCallback> disconnectCallbacks = Subscribers.create();
  private final Collection<PendingPeerRequest> pendingRequests = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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

  public void registerConnection(
      final PeerConnection peerConnection, final List<PeerValidator> peerValidators) {
    // TODO: Stefan: this needs to check whether there is an existing EthPeer, If yes, wait for a
    // callback being called before deciding which connection to keep
    //  Put the new connection into a map (newConnection, existingEthPeer)
    synchronized (this) {
      final Bytes peerId = peerConnection.getPeer().getId();
      LOG.info(
          "Regestering a connection with peer {}, connection {}",
          peerId,
          System.identityHashCode(peerConnection));
      final Optional<EthPeer> optionalEthPeer =
          nonReadyConnections.entrySet().stream()
              .map(e -> e.getValue())
              .filter(pe -> pe.getConnection().getPeer().getId().equals(peerId))
              .findFirst();
      if (optionalEthPeer.isPresent()) {
        nonReadyConnections.put(peerConnection, optionalEthPeer.get());
        LOG.info(
            "Found existing EthPeer in nonReadyConnections for peer {}, connection {}",
            optionalEthPeer.get(),
            System.identityHashCode(peerConnection));
      } else {
        connections.compute(
            peerId,
            (c, p) -> {
              final EthPeer peer;
              if (p == null) {
                peer =
                    new EthPeer(
                        peerConnection,
                        protocolName,
                        peerValidators,
                        clock,
                        permissioningProviders);
                LOG.info(
                    "Created new EthPeer in connections for peer {}, connection {}",
                    peer,
                    System.identityHashCode(peerConnection));

              } else {
                LOG.info(
                    "Found existing EthPeer in connections for peer {}, connection {}",
                    p,
                    System.identityHashCode(peerConnection));
                peer = p;
              }

              nonReadyConnections.put(peerConnection, peer);

              // TODO: We might want to schedule using the ctx
              // this makes sure that the content of the nonReadyConnections is cleaned up. The connection has 30s time to create a "ready" connection. If this doesn't happen
              scheduler.schedule(
                  () -> {
                    try {
                      final EthPeer removed = nonReadyConnections.remove(peerConnection);
                      if (removed != null && removed.getConnection() != peerConnection) {
                        peerConnection.disconnect(
                                DisconnectMessage.DisconnectReason.ALREADY_CONNECTED);
                      }
                    } catch (final Throwable t) {
                      LOG.info("Caught Throwable in ScheduledExecutorService {}", t);
                    }
                  },
                  30,
                  TimeUnit.SECONDS);
              return p; // We do not add the new EthPeer here, because this can only happen when it
              // is "ready"
            });
      }
    }
  }

  public void registerDisconnect(final PeerConnection connection) {
    connections.compute(
        connection.getPeer().getId(),
        (id, existingPeer) -> {
          if (existingPeer != null) {
            if (existingPeer.getConnection().equals(connection)) {
              LOG.info(
                  "Removing peer {} with connection {} from EthPeers",
                  connection.getPeer().getId(),
                  System.identityHashCode(connection));
              disconnectCallbacks.forEach(callback -> callback.onDisconnect(existingPeer));
              existingPeer.handleDisconnect();
              abortPendingRequestsAssignedToDisconnectedPeers();
              return null;
            } else {
              // if this is called for a different connection we keep the existing peer
              return existingPeer;
            }
          } else {
            return null;
          }
        });

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
    // potentially has to use the "new" connection
    return nonReadyConnections.getOrDefault(
        peerConnection, connections.get(peerConnection.getPeer().getId()));
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

  public Stream<EthPeer> streamBestPeers() {
    return streamAllPeers().sorted(BEST_CHAIN.reversed());
  }

  public Optional<EthPeer> bestPeer() {
    return streamAllPeers().max(BEST_CHAIN);
  }

  public Optional<EthPeer> bestPeerWithHeightEstimate() {
    return bestPeerMatchingCriteria(
        p -> {
          final boolean fullyValidated = p.isFullyValidated();
          final boolean hasEstimatedHeight = p.chainState().hasEstimatedHeight();
          LOG.info(
              "Peer {} is fully validated: {}, hasEstimatedHeight: {}, connection: {}",
              p.getConnection().getPeer().getId(),
              fullyValidated,
              hasEstimatedHeight,
              System.identityHashCode(p.getConnection()));
          return fullyValidated && hasEstimatedHeight;
        });
  }

  public Optional<EthPeer> bestPeerMatchingCriteria(final Predicate<EthPeer> matchesCriteria) {
    return streamAllPeers().filter(matchesCriteria).max(BEST_CHAIN);
  }

  public void invokeConnectionCallbacks(final EthPeer peer) {
    connectCallbacks.forEach(cb -> cb.onPeerConnected(peer));
  }

  public void maybeUseReadyConnection(final PeerConnection peerConnection) {
    synchronized (this) {
      if (peerConnection
          .callOnConnectionReadyCallback()) { // returns true if we are to use this connection
        connections.compute(
            peerConnection.getPeer().getId(),
            (id, p) -> {
              if (p != null) { // existing connection
                // disconnect the old connection in 30 seconds, because it could have been used for
                // requests
                //            scheduler.schedule(() ->
                // p.getConnection().disconnect(DisconnectMessage.DisconnectReason.ALREADY_CONNECTED), 30, TimeUnit.SECONDS); // TODO: can we do the clean up without the scheduler?
                p.replaceConnection(peerConnection);
                return p;
              } else {
                invokeConnectionCallbacks(nonReadyConnections.get(peerConnection));
                return nonReadyConnections.get(peerConnection);
              }
            });
      }
      //      else { // connection has not been used, so it can be closed
      //        nonReadyConnections.remove(peerConnection);
      //        peerConnection.disconnect(DisconnectMessage.DisconnectReason.ALREADY_CONNECTED);
      //      }
    }
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
}
