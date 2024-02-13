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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer.DisconnectCallback;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.forkid.ForkId;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthPeers {
  private static final Logger LOG = LoggerFactory.getLogger(EthPeers.class);
  public static final Comparator<EthPeer> TOTAL_DIFFICULTY =
      Comparator.comparing((final EthPeer p) -> p.chainState().getEstimatedTotalDifficulty());

  public static final Comparator<EthPeer> CHAIN_HEIGHT =
      Comparator.comparing((final EthPeer p) -> p.chainState().getEstimatedHeight());

  public static final Comparator<EthPeer> MOST_USEFUL_PEER =
      Comparator.comparing((final EthPeer p) -> p.getReputation().getScore())
          .thenComparing(CHAIN_HEIGHT);

  public static final Comparator<EthPeer> HEAVIEST_CHAIN =
      TOTAL_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  public static final Comparator<EthPeer> LEAST_TO_MOST_BUSY =
      Comparator.comparing(EthPeer::outstandingRequests)
          .thenComparing(EthPeer::getLastRequestTimestamp);
  public static final int NODE_ID_LENGTH = 64;
  public static final int USEFULL_PEER_SCORE_THRESHOLD = 102;

  private final Map<Bytes, EthPeer> activeConnections = new ConcurrentHashMap<>();

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
  private final int peerUpperBound;
  private final int maxRemotelyInitiatedConnections;
  private final Boolean randomPeerPriority;
  private final Bytes nodeIdMask = Bytes.random(NODE_ID_LENGTH);
  private final Supplier<ProtocolSpec> currentProtocolSpecSupplier;
  private final SyncMode syncMode;
  private final ForkIdManager forkIdManager;
  private final int snapServerTargetNumber;

  private Comparator<EthPeer> bestPeerComparator;
  private final Bytes localNodeId;
  private RlpxAgent rlpxAgent;

  private final Counter connectedPeersCounter;
  private List<ProtocolManager> protocolManagers;

  public EthPeers(
      final String protocolName,
      final Supplier<ProtocolSpec> currentProtocolSpecSupplier,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final int maxMessageSize,
      final List<NodeMessagePermissioningProvider> permissioningProviders,
      final Bytes localNodeId,
      final int peerUpperBound,
      final int maxRemotelyInitiatedConnections,
      final Boolean randomPeerPriority,
      final SyncMode syncMode,
      final ForkIdManager forkIdManager) {
    this.protocolName = protocolName;
    this.currentProtocolSpecSupplier = currentProtocolSpecSupplier;
    this.clock = clock;
    this.permissioningProviders = permissioningProviders;
    this.maxMessageSize = maxMessageSize;
    this.bestPeerComparator = HEAVIEST_CHAIN;
    this.localNodeId = localNodeId;
    this.peerUpperBound = peerUpperBound;
    this.maxRemotelyInitiatedConnections = maxRemotelyInitiatedConnections;
    this.randomPeerPriority = randomPeerPriority;
    LOG.trace("MaxPeers: {}, Max Remote: {}", peerUpperBound, maxRemotelyInitiatedConnections);
    this.syncMode = syncMode;
    this.forkIdManager = forkIdManager;
    if (syncMode == SyncMode.X_CHECKPOINT || syncMode == SyncMode.X_SNAP) {
      snapServerTargetNumber =
          peerUpperBound / 2; // hardcoded for now. 50% of peers should be snap servers
    } else {
      snapServerTargetNumber = 0;
    }
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
      EthPeer ethPeer = activeConnections.get(id);
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

  @Nonnull
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
        removed = activeConnections.remove(id, peer);
        disconnectCallbacks.forEach(callback -> callback.onDisconnect(peer));
        peer.handleDisconnect();
        abortPendingRequestsAssignedToDisconnectedPeers();
        if (peer.getReputation().getScore() > USEFULL_PEER_SCORE_THRESHOLD) {
          LOG.debug("Disconnected USEFULL peer {}", peer);
        } else {
          LOG.debug("Disconnected EthPeer {}", peer.getLoggableId());
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
    return ethPeer != null ? ethPeer : activeConnections.get(connection.getPeer().getId());
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
    return activeConnections.size();
  }

  public int getMaxPeers() {
    return peerUpperBound;
  }

  public Stream<EthPeer> streamAllPeers() {
    return activeConnections.values().stream();
  }

  private void removeDisconnectedPeers() {
    activeConnections
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
    return activeConnections.values().stream()
        .map(EthPeer::getConnection)
        .filter(c -> !c.isDisconnected());
  }

  public Stream<PeerConnection> getAllConnections() {
    return Stream.concat(
            activeConnections.values().stream().map(EthPeer::getConnection),
            incompleteConnections.asMap().keySet().stream())
        .distinct()
        .filter(c -> !c.isDisconnected());
  }

  public boolean shouldTryToConnect(final Peer peer, final boolean inbound) {

    if (peer.getForkId().isPresent()) {
      final ForkId forkId = peer.getForkId().get();
      if (!forkIdManager.peerCheck(forkId)) {
        LOG.atDebug()
            .setMessage("Wrong fork id, not trying to connect to peer {}")
            .addArgument(peer::getId)
            .log();

        return false;
      }
    }

    final Bytes id = peer.getId();
    if (alreadyConnectedOrConnecting(inbound, id)) {
      return false;
    }

    if (peerCount() < getMaxPeers() || canExceedPeerLimits(id)) {
      return true;
    }

    // ask the protocol managers whether they want to connect to this peer and if none of them want
    // to connect, then we don't connect
    return protocolManagers.stream().anyMatch(p -> p.shouldTryToConnect(peer, inbound));
  }

  private boolean alreadyConnectedOrConnecting(final boolean inbound, final Bytes id) {
    final EthPeer ethPeer = activeConnections.get(id);
    if (ethPeer != null && !ethPeer.isDisconnected()) {
      return true;
    }
    final List<PeerConnection> incompleteConnections = getIncompleteConnections(id);
    if (!incompleteConnections.isEmpty()) {
      if (incompleteConnections.stream()
          .anyMatch(c -> !c.isDisconnected() && (!inbound || (inbound && c.inboundInitiated())))) {
        return true;
      }
    }
    return false;
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
                  .addArgument(peer::getLoggableId)
                  .addArgument(this::peerCount)
                  .addArgument(this::getMaxPeers)
                  .log();
              peer.disconnect(DisconnectMessage.DisconnectReason.USELESS_PEER);
            });
  }

  public void setProtocolManagers(final List<ProtocolManager> protocolManagers) {
    this.protocolManagers = protocolManagers;
  }

  @FunctionalInterface
  public interface ConnectCallback {
    void onPeerConnected(EthPeer newPeer);
  }

  @Override
  public String toString() {
    if (activeConnections.isEmpty()) {
      return "0 EthPeers {}";
    }
    final String connectionsList =
        activeConnections.values().stream()
            .sorted()
            .map(EthPeer::toString)
            .collect(Collectors.joining(", \n"));
    return activeConnections.size() + " EthPeers {\n" + connectionsList + '}';
  }

  private void ethPeerStatusExchanged(final EthPeer peer) {
    // We have a connection to a peer that is on the right chain and is willing to connect to us.
    // Find out what the EthPeer block hight is and whether it can serve snap data (if we are doing
    // snap sync)
    CompletableFuture<Void> future =
        CompletableFuture.runAsync(
            () -> {
              // Call your function here
              try {
                final RequestManager.ResponseStream responseStream =
                    peer.getHeadersByHash(peer.chainState().getBestBlock().getHash(), 1, 0, false);
                responseStream.then(
                    (closed, msg, p) -> {
                      if (closed) {
                        throw new RuntimeException("Peer disconnected");
                      }
                      final List<BlockHeader> blockHeaders =
                          new BytesValueRLPInput(msg.getData(), false)
                              .readList(
                                  rlp ->
                                      BlockHeader.readFrom(rlp, new MainnetBlockHeaderFunctions()));
                      if (blockHeaders.isEmpty()) {
                        // empty response, disconnect
                        p.disconnect(DisconnectMessage.DisconnectReason.USELESS_PEER);
                        throw new RuntimeException("Empty response");
                      }
                      final BlockHeader header = blockHeaders.get(0);
                      p.chainState().updateHeightEstimate(header.getNumber());
                      // make sure that the enforceTrailingPeerLimit() from ChainHeadTracker
                      // equivalent is done here, instead of after adding to EthPeers
                      // TODO: once we have the height we could do a SNAP request here to figure out
                      // whether the peer is serving snap data
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });

    CompletableFuture<Void> isServingSnapFuture;
    if (syncMode == SyncMode.X_CHECKPOINT || syncMode == SyncMode.X_SNAP) {
      isServingSnapFuture =
          CompletableFuture.runAsync(
              () -> {
                checkIsSnapServer(peer);
              });
    } else {
      isServingSnapFuture = CompletableFuture.completedFuture(null);
    }

    // join the two futures
    CompletableFuture.allOf(future, isServingSnapFuture)
        .thenRun(
            () -> {
              if (!peer.getConnection().isDisconnected() && addPeerToEthPeers(peer)) {
                connectedPeersCounter.inc();
                connectCallbacks.forEach(cb -> cb.onPeerConnected(peer));
              }
            });
  }

  private void checkIsSnapServer(final EthPeer peer) {
    if (peer.getAgreedCapabilities().contains(SnapProtocol.SNAP1)) {
      // could try and retrieve some SNAP data here to check that (e.g. GetByteCodes for a small
      // contract)
      LOG.info("XXXXXXX" + peer.getConnection().getPeerInfo().getClientId());
      peer.setIsServingSnap(peer.getConnection().getPeerInfo().getClientId().contains("Geth"));
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
    return activeConnections.values().stream()
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
    return activeConnections.values().stream()
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
    // Figure out whether we want to keep this peer to add it to the active connections.
    final PeerConnection connection = peer.getConnection();
    if (activeConnections.containsValue(peer)) {
      connection.disconnect(DisconnectMessage.DisconnectReason.ALREADY_CONNECTED);
      return false;
    }
    final Bytes id = peer.getId();
    if (!randomPeerPriority) {
      // Disconnect if too many peers
      if (peerCount() >= peerUpperBound) {
        if (needMoreSnapServers()) {
          disconnectNonSnapServerPeerOrLeastUseful();
        } else if (!canExceedPeerLimits(id)) {
          LOG.trace(
              "Too many peers. Disconnect connection: {}, max connections {}",
              connection,
              peerUpperBound);
          connection.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS);
          return false;
        }
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
      final boolean added = (activeConnections.putIfAbsent(id, peer) == null);
      if (added) {
        LOG.trace("Added peer {} with connection {} to completeConnections", id, connection);
      } else {
        LOG.trace("Did not add peer {} with connection {} to completeConnections", id, connection);
      }
      return added;
    } else {
      // randomPeerPriority! Add the peer and if there are too many connections fix it
      // TODO: random peer priority does not care yet about snap server peers -> check later
      activeConnections.putIfAbsent(id, peer);
      enforceRemoteConnectionLimits();
      enforceConnectionLimits();
      return activeConnections.containsKey(id);
    }
  }

  private boolean needMoreSnapServers() {
    return activeConnections.values().stream().filter(EthPeer::isServingSnap).count()
        < snapServerTargetNumber;
  }

  private void disconnectNonSnapServerPeerOrLeastUseful() {
    activeConnections.values().stream()
        .filter(p -> !p.isServingSnap())
        .min(MOST_USEFUL_PEER)
        .ifPresentOrElse(
            p -> p.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS),
            () ->
                activeConnections.values().stream()
                    .min(MOST_USEFUL_PEER)
                    .ifPresent(
                        p -> p.disconnect(DisconnectMessage.DisconnectReason.TOO_MANY_PEERS)));
  }
}
