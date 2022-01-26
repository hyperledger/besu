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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerBondedObserver;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryEvent;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.peers.PeerId;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component is the entrypoint for managing the lifecycle of peers.
 *
 * <p>It keeps track of the interactions with each peer, including the expectations of what we
 * expect to receive next from each peer. In other words, it implements the state machine for
 * (discovery) peers.
 *
 * <p>When necessary, it updates the underlying {@link PeerTable}, particularly with additions which
 * may succeed or not depending on the contents of the target bucket for the peer.
 *
 * <h3>Peer state machine</h3>
 *
 * <pre>{@code
 *                                                                +--------------------+
 *                                                                |                    |
 *                                                    +-----------+  MESSAGE_EXPECTED  +-----------+
 *                                                    |           |                    |           |
 *                                                    |           +---+----------------+           |
 * +------------+         +-----------+         +-----+----+          |                      +-----v-----+
 * |            |         |           |         |          <----------+                      |           |
 * |  KNOWN  +--------->  BONDING  +--------->  BONDED     |                                 |  DROPPED  |
 * |            |         |           |         |          ^                                 |           |
 * +------------+         +-----------+         +----------+                                 +-----------+
 *
 * }</pre>
 *
 * <ul>
 *   <li><em>KNOWN:</em> the peer is known but there is no ongoing interaction with it.
 *   <li><em>BONDING:</em> an attempt to bond is being made (e.g. a PING has been sent).
 *   <li><em>BONDED:</em> the bonding handshake has taken place (e.g. an expected PONG has been
 *       received after having sent a PING). This is the same as having an "active" channel.
 *   <li><em>MESSAGE_EXPECTED (*)</em>: a message has been sent and a response is expected.
 *   <li><em>DROPPED (*):</em> the peer is no longer in our peer table.
 * </ul>
 *
 * <p>(*) It is worthy to note that the <code>MESSAGE_EXPECTED</code> and <code>DROPPED</code>
 * states are not modelled explicitly in {@link PeerDiscoveryStatus}, but they have been included in
 * the diagram for clarity. These two states define the elimination path for a peer from the
 * underlying table.
 *
 * <p>If an expectation to receive a message was unmet, following the evaluation of a failure
 * condition, the peer will be physically dropped (eliminated) from the table.
 */
public class PeerDiscoveryController {
  private static final Logger LOG = LoggerFactory.getLogger(PeerDiscoveryController.class);
  private static final long REFRESH_CHECK_INTERVAL_MILLIS = MILLISECONDS.convert(30, SECONDS);
  private static final int PEER_REFRESH_ROUND_TIMEOUT_IN_SECONDS = 5;
  protected final TimerUtil timerUtil;
  private final PeerTable peerTable;
  private final Cache<Bytes, DiscoveryPeer> bondingPeers =
      CacheBuilder.newBuilder().maximumSize(50).expireAfterWrite(10, TimeUnit.MINUTES).build();

  private final Collection<DiscoveryPeer> bootstrapNodes;

  /* A tracker for inflight interactions and the state machine of a peer. */
  private final Map<Bytes, PeerInteractionState> inflightInteractions = new ConcurrentHashMap<>();

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final NodeKey nodeKey;
  // The peer representation of this node
  private final DiscoveryPeer localPeer;
  private final OutboundMessageHandler outboundMessageHandler;
  private final PeerDiscoveryPermissions peerPermissions;
  private final DiscoveryProtocolLogger discoveryProtocolLogger;
  private final LabelledMetric<Counter> interactionCounter;
  private final LabelledMetric<Counter> interactionRetryCounter;

  private RetryDelayFunction retryDelayFunction = RetryDelayFunction.linear(1.5, 2000, 60000);

  private final AsyncExecutor workerExecutor;

  private final PeerRequirement peerRequirement;
  private final long tableRefreshIntervalMs;
  private OptionalLong tableRefreshTimerId = OptionalLong.empty();
  private long lastRefreshTime = -1;

  private final long cleanPeerTableIntervalMs;
  private final AtomicBoolean peerTableIsDirty = new AtomicBoolean(false);
  private OptionalLong cleanTableTimerId = OptionalLong.empty();

  // Observers for "peer bonded" discovery events.
  private final Subscribers<PeerBondedObserver> peerBondedObservers;

  private RecursivePeerRefreshState recursivePeerRefreshState;

  private PeerDiscoveryController(
      final NodeKey nodeKey,
      final DiscoveryPeer localPeer,
      final PeerTable peerTable,
      final Collection<DiscoveryPeer> bootstrapNodes,
      final OutboundMessageHandler outboundMessageHandler,
      final TimerUtil timerUtil,
      final AsyncExecutor workerExecutor,
      final long tableRefreshIntervalMs,
      final long cleanPeerTableIntervalMs,
      final PeerRequirement peerRequirement,
      final PeerPermissions peerPermissions,
      final Subscribers<PeerBondedObserver> peerBondedObservers,
      final MetricsSystem metricsSystem) {
    this.timerUtil = timerUtil;
    this.nodeKey = nodeKey;
    this.localPeer = localPeer;
    this.bootstrapNodes = bootstrapNodes;
    this.peerTable = peerTable;
    this.workerExecutor = workerExecutor;
    this.tableRefreshIntervalMs = tableRefreshIntervalMs;
    this.cleanPeerTableIntervalMs = cleanPeerTableIntervalMs;
    this.peerRequirement = peerRequirement;
    this.outboundMessageHandler = outboundMessageHandler;
    this.peerBondedObservers = peerBondedObservers;
    this.discoveryProtocolLogger = new DiscoveryProtocolLogger(metricsSystem);

    this.peerPermissions = new PeerDiscoveryPermissions(localPeer, peerPermissions);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.NETWORK,
        "discovery_inflight_interactions_current",
        "Current number of inflight discovery interactions",
        inflightInteractions::size);

    interactionCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.NETWORK,
            "discovery_interaction_count",
            "Total number of discovery interactions initiated",
            "type");

    interactionRetryCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.NETWORK,
            "discovery_interaction_retry_count",
            "Total number of interaction retries performed",
            "type");
  }

  public static Builder builder() {
    return new Builder();
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("The peer table had already been started");
    }

    final List<DiscoveryPeer> initialDiscoveryPeers =
        bootstrapNodes.stream()
            .filter(peerPermissions::isAllowedInPeerTable)
            .collect(Collectors.toList());
    initialDiscoveryPeers.forEach(peerTable::tryAdd);

    recursivePeerRefreshState =
        new RecursivePeerRefreshState(
            this::bond,
            this::findNodes,
            timerUtil,
            localPeer,
            peerTable,
            peerPermissions,
            PEER_REFRESH_ROUND_TIMEOUT_IN_SECONDS,
            100);

    peerPermissions.subscribeUpdate(this::handlePermissionsUpdate);

    recursivePeerRefreshState.start(initialDiscoveryPeers, localPeer.getId());

    final long refreshTimerId =
        timerUtil.setPeriodic(
            Math.min(REFRESH_CHECK_INTERVAL_MILLIS, tableRefreshIntervalMs),
            this::refreshTableIfRequired);
    tableRefreshTimerId = OptionalLong.of(refreshTimerId);

    cleanTableTimerId =
        OptionalLong.of(
            timerUtil.setPeriodic(cleanPeerTableIntervalMs, this::cleanPeerTableIfRequired));
  }

  public CompletableFuture<?> stop() {
    if (!started.compareAndSet(true, false)) {
      return CompletableFuture.completedFuture(null);
    }

    tableRefreshTimerId.ifPresent(timerUtil::cancelTimer);
    tableRefreshTimerId = OptionalLong.empty();
    cleanTableTimerId.ifPresent(timerUtil::cancelTimer);
    cleanTableTimerId = OptionalLong.empty();
    inflightInteractions.values().forEach(PeerInteractionState::cancelTimers);
    inflightInteractions.clear();
    return CompletableFuture.completedFuture(null);
  }

  private void handlePermissionsUpdate(
      final boolean addRestrictions, final Optional<List<Peer>> affectedPeers) {
    if (!addRestrictions) {
      // Nothing to do if permissions were relaxed
      return;
    }

    // If we have an explicit list of peers, drop each peer from our discovery table
    if (affectedPeers.isPresent()) {
      affectedPeers.get().forEach(this::dropPeerIfDisallowed);
      return;
    }

    // Otherwise, signal that we need to clean up the peer table
    peerTableIsDirty.set(true);
  }

  private void dropPeerIfDisallowed(final Peer peer) {
    if (!peerPermissions.isAllowedInPeerTable(peer)) {
      dropPeer(peer);
    }
  }

  public void dropPeer(final PeerId peer) {
    peerTable.tryEvict(peer);
  }

  /**
   * Handles an incoming message and processes it based on the state machine for the {@link
   * DiscoveryPeer}.
   *
   * <p>The callback will be called with the canonical representation of the sender Peer as stored
   * in our table, or with an empty Optional if the message was out of band and we didn't process
   * it.
   *
   * @param packet The incoming message.
   * @param sender The sender.
   */
  public void onMessage(final Packet packet, final DiscoveryPeer sender) {
    discoveryProtocolLogger.logReceivedPacket(sender, packet);

    // Message from self. This should not happen.
    if (sender.getId().equals(localPeer.getId())) {
      return;
    }

    final DiscoveryPeer peer = resolvePeer(sender);
    switch (packet.getType()) {
      case PING:
        if (peerPermissions.allowInboundBonding(peer)) {
          peer.setLastSeen(System.currentTimeMillis());
          final PingPacketData ping = packet.getPacketData(PingPacketData.class).get();
          if (!PeerDiscoveryStatus.BONDED.equals(peer.getStatus())
              && (bondingPeers.getIfPresent(sender.getId()) == null)) {
            bond(peer);
          }
          respondToPing(ping, packet.getHash(), peer);
        }
        break;
      case PONG:
        matchInteraction(packet)
            .ifPresent(
                interaction -> {
                  bondingPeers.invalidate(peer.getId());
                  addToPeerTable(peer);
                  recursivePeerRefreshState.onBondingComplete(peer);
                });
        break;
      case NEIGHBORS:
        matchInteraction(packet)
            .ifPresent(
                interaction ->
                    recursivePeerRefreshState.onNeighboursReceived(
                        peer, getPeersFromNeighborsPacket(packet)));
        break;
      case FIND_NEIGHBORS:
        if (PeerDiscoveryStatus.BONDED.equals(peer.getStatus())
            && peerPermissions.allowInboundNeighborsRequest(peer)) {
          final FindNeighborsPacketData fn =
              packet.getPacketData(FindNeighborsPacketData.class).get();
          respondToFindNeighbors(fn, peer);
        }

        break;
      case ENR_REQUEST:
        if (PeerDiscoveryStatus.BONDED.equals(peer.getStatus())) {
          LOG.trace("ENR_REQUEST received from bonded peer Id: {}", peer.getId());
          packet
              .getPacketData(ENRRequestPacketData.class)
              .ifPresent(p -> respondToENRRequest(p, packet.getHash(), peer));
        }

        break;
      case ENR_RESPONSE:
        // Currently there is no use case where an ENRResponse will be sent otherwise
        // logic can be added here to query and store the response ENRs
        packet
            .getPacketData(ENRResponsePacketData.class)
            .filter(p -> p.getEnr().getNodeId().equals(sender.getId()))
            .ifPresent(p -> LOG.debug("Received NodeRecord: {}", p.getEnr().asEnr()));

        break;
    }
  }

  private List<DiscoveryPeer> getPeersFromNeighborsPacket(final Packet packet) {
    final Optional<NeighborsPacketData> maybeNeighborsData =
        packet.getPacketData(NeighborsPacketData.class);
    if (maybeNeighborsData.isEmpty()) {
      return Collections.emptyList();
    }
    final NeighborsPacketData neighborsData = maybeNeighborsData.get();

    return neighborsData.getNodes().stream()
        .map(p -> peerTable.get(p).orElse(p))
        .collect(Collectors.toList());
  }

  private boolean addToPeerTable(final DiscoveryPeer peer) {
    if (!peerPermissions.isAllowedInPeerTable(peer)) {
      return false;
    }

    final PeerTable.AddResult result = peerTable.tryAdd(peer);
    if (result.getOutcome() == PeerTable.AddResult.AddOutcome.SELF) {
      return false;
    }

    // Reset the last seen timestamp.
    final long now = System.currentTimeMillis();
    if (peer.getFirstDiscovered() == 0) {
      peer.setFirstDiscovered(now);
    }
    peer.setLastSeen(now);

    if (peer.getStatus() != PeerDiscoveryStatus.BONDED) {
      peer.setStatus(PeerDiscoveryStatus.BONDED);
      notifyPeerBonded(peer, now);
    }

    if (result.getOutcome() == PeerTable.AddResult.AddOutcome.ALREADY_EXISTED) {
      // Bump peer.
      peerTable.tryEvict(peer);
      peerTable.tryAdd(peer);
    } else if (result.getOutcome() == PeerTable.AddResult.AddOutcome.BUCKET_FULL) {
      peerTable.tryEvict(result.getEvictionCandidate());
      peerTable.tryAdd(peer);
    }

    return true;
  }

  private void notifyPeerBonded(final DiscoveryPeer peer, final long now) {
    final PeerDiscoveryEvent.PeerBondedEvent event =
        new PeerDiscoveryEvent.PeerBondedEvent(peer, now);
    dispatchPeerBondedEvent(peerBondedObservers, event);
  }

  private Optional<PeerInteractionState> matchInteraction(final Packet packet) {
    final PeerInteractionState interaction = inflightInteractions.get(packet.getNodeId());
    if (interaction == null || !interaction.test(packet)) {
      return Optional.empty();
    }
    interaction.cancelTimers();
    inflightInteractions.remove(packet.getNodeId());
    return Optional.of(interaction);
  }

  private void refreshTableIfRequired() {
    final long now = System.currentTimeMillis();
    if (lastRefreshTime + tableRefreshIntervalMs <= now) {
      LOG.debug("Peer table refresh triggered by timer expiry");
      refreshTable();
    } else if (!peerRequirement.hasSufficientPeers()) {
      LOG.debug("Peer table refresh triggered by insufficient peers");
      refreshTable();
    }
  }

  private void cleanPeerTableIfRequired() {
    if (peerTableIsDirty.compareAndSet(true, false)) {
      peerTable.streamAllPeers().forEach(this::dropPeerIfDisallowed);
    }
  }

  @VisibleForTesting
  RecursivePeerRefreshState getRecursivePeerRefreshState() {
    return recursivePeerRefreshState;
  }

  /**
   * Refreshes the peer table by generating a random ID and interrogating the closest nodes for it.
   * Currently the refresh process is NOT recursive.
   */
  private void refreshTable() {
    final Bytes target = Peer.randomId();
    final List<DiscoveryPeer> initialPeers = peerTable.nearestPeers(Peer.randomId(), 16);
    recursivePeerRefreshState.start(initialPeers, target);
    lastRefreshTime = System.currentTimeMillis();
  }

  /**
   * Initiates a bonding PING-PONG cycle with a peer.
   *
   * @param peer The targeted peer.
   */
  @VisibleForTesting
  void bond(final DiscoveryPeer peer) {
    peer.setFirstDiscovered(System.currentTimeMillis());
    peer.setStatus(PeerDiscoveryStatus.BONDING);
    bondingPeers.put(peer.getId(), peer);

    final Consumer<PeerInteractionState> action =
        interaction -> {
          final PingPacketData data =
              PingPacketData.create(
                  Optional.of(localPeer.getEndpoint()),
                  peer.getEndpoint(),
                  localPeer.getNodeRecord().map(NodeRecord::getSeq).orElse(null));
          createPacket(
              PacketType.PING,
              data,
              pingPacket -> {
                final Bytes pingHash = pingPacket.getHash();
                // Update the matching filter to only accept the PONG if it echoes the hash of our
                // PING.
                final Predicate<Packet> newFilter =
                    packet ->
                        packet
                            .getPacketData(PongPacketData.class)
                            .map(pong -> pong.getPingHash().equals(pingHash))
                            .orElse(false);
                interaction.updateFilter(newFilter);

                sendPacket(peer, pingPacket);
              });
        };

    // The filter condition will be updated as soon as the action is performed.
    final PeerInteractionState peerInteractionState =
        new PeerInteractionState(action, peer.getId(), PacketType.PONG, packet -> false, true);
    dispatchInteraction(peer, peerInteractionState);
  }

  private void sendPacket(final DiscoveryPeer peer, final PacketType type, final PacketData data) {
    createPacket(
        type,
        data,
        packet -> {
          discoveryProtocolLogger.logSendingPacket(peer, packet);
          outboundMessageHandler.send(peer, packet);
        });
  }

  private void sendPacket(final DiscoveryPeer peer, final Packet packet) {
    discoveryProtocolLogger.logSendingPacket(peer, packet);
    outboundMessageHandler.send(peer, packet);
  }

  @VisibleForTesting
  void createPacket(final PacketType type, final PacketData data, final Consumer<Packet> handler) {
    // Creating packets is quite expensive because they have to be cryptographically signed
    // So ensure the work is done on a worker thread to avoid blocking the vertx event thread.
    workerExecutor
        .execute(() -> Packet.create(type, data, nodeKey))
        .thenAccept(handler)
        .exceptionally(
            error -> {
              LOG.error("Error while creating packet", error);
              return null;
            });
  }

  /**
   * Sends a FIND_NEIGHBORS message to a {@link DiscoveryPeer}, in search of a target value.
   *
   * @param peer the peer to interrogate
   * @param target the target node ID to find
   */
  private void findNodes(final DiscoveryPeer peer, final Bytes target) {
    final Consumer<PeerInteractionState> action =
        interaction -> {
          final FindNeighborsPacketData data = FindNeighborsPacketData.create(target);
          sendPacket(peer, PacketType.FIND_NEIGHBORS, data);
        };
    final PeerInteractionState interaction =
        new PeerInteractionState(action, peer.getId(), PacketType.NEIGHBORS, packet -> true, true);
    dispatchInteraction(peer, interaction);
  }

  /**
   * Dispatches a new tracked interaction with a peer, adding it to the {@link
   * #inflightInteractions} map and executing the action for the first time.
   *
   * <p>If a previous inflightInteractions interaction existed, we cancel any associated timers.
   *
   * @param peer The peer.
   * @param state The state.
   */
  private void dispatchInteraction(final Peer peer, final PeerInteractionState state) {
    final PeerInteractionState previous = inflightInteractions.put(peer.getId(), state);
    if (previous != null) {
      previous.cancelTimers();
    }
    state.execute(0, 0);
  }

  private void respondToPing(
      final PingPacketData packetData, final Bytes pingHash, final DiscoveryPeer sender) {
    if (packetData.getExpiration() < Instant.now().getEpochSecond()) {
      LOG.debug("ignoring expired PING");
      return;
    }
    // We don't care about the `from` field of the ping, we pong to the `sender`
    final PongPacketData data =
        PongPacketData.create(
            sender.getEndpoint(),
            pingHash,
            localPeer.getNodeRecord().map(NodeRecord::getSeq).orElse(null));

    sendPacket(sender, PacketType.PONG, data);
  }

  private void respondToFindNeighbors(
      final FindNeighborsPacketData packetData, final DiscoveryPeer sender) {
    if (packetData.getExpiration() < Instant.now().getEpochSecond()) {
      return;
    }
    // Each peer is encoded as 16 bytes for address, 4 bytes for port, 4 bytes for tcp port
    // and 64 bytes for id. This is prepended by 97 bytes of hash, signature and type.
    // 16 + 4 + 4 + 64 = 88 bytes
    // 88 * 13 = 1144 bytes
    // To fit under 1280 bytes, we must return just 13 peers maximum.
    final List<DiscoveryPeer> peers = peerTable.nearestPeers(packetData.getTarget(), 13);
    final PacketData data = NeighborsPacketData.create(peers);
    sendPacket(sender, PacketType.NEIGHBORS, data);
  }

  private void respondToENRRequest(
      final ENRRequestPacketData enrRequestPacketData,
      final Bytes requestHash,
      final DiscoveryPeer sender) {
    if (enrRequestPacketData.getExpiration() >= Instant.now().getEpochSecond()) {
      final ENRResponsePacketData data =
          ENRResponsePacketData.create(requestHash, localPeer.getNodeRecord().orElse(null));
      sendPacket(sender, PacketType.ENR_RESPONSE, data);
    }
  }

  // Dispatches an event to a set of observers.
  private void dispatchPeerBondedEvent(
      final Subscribers<PeerBondedObserver> observers,
      final PeerDiscoveryEvent.PeerBondedEvent event) {
    observers.forEach(observer -> observer.onPeerBonded(event));
  }

  /**
   * Returns a copy of the known peers. Modifications to the list will not update the table's state,
   * but modifications to the Peers themselves will.
   *
   * @return List of peers.
   */
  public Stream<DiscoveryPeer> streamDiscoveredPeers() {
    return peerTable.streamAllPeers().filter(peerPermissions::isAllowedInPeerTable);
  }

  public void setRetryDelayFunction(final RetryDelayFunction retryDelayFunction) {
    this.retryDelayFunction = retryDelayFunction;
  }

  public void handleBondingRequest(final DiscoveryPeer peer) {
    final DiscoveryPeer peerToBond = resolvePeer(peer);

    if (peerPermissions.allowOutboundBonding(peerToBond)
        && PeerDiscoveryStatus.KNOWN.equals(peerToBond.getStatus())) {
      bond(peerToBond);
    }
  }

  // Load the peer first from the table, then from bonding cache or use the instance that comes in.
  private DiscoveryPeer resolvePeer(final DiscoveryPeer peer) {
    final Optional<DiscoveryPeer> maybeKnownPeer =
        peerTable.get(peer).filter(known -> known.discoveryEndpointMatches(peer));
    DiscoveryPeer resolvedPeer = maybeKnownPeer.orElse(peer);
    if (maybeKnownPeer.isEmpty()) {
      DiscoveryPeer bondingPeer = bondingPeers.getIfPresent(peer.getId());
      if (bondingPeer != null) {
        resolvedPeer = bondingPeer;
      }
    }

    return resolvedPeer;
  }

  /** Holds the state machine data for a peer interaction. */
  private class PeerInteractionState implements Predicate<Packet> {

    private static final int MAX_RETRIES = 5;
    /**
     * The action that led to the peer being in this state (e.g. sending a PING or NEIGHBORS
     * message), in case it needs to be retried.
     */
    private final Consumer<PeerInteractionState> action;

    private final Bytes peerId;
    /** The expected type of the message that will transition the peer out of this state. */
    private final PacketType expectedType;

    private final Counter retryCounter;
    /** A custom filter to accept transitions out of this state. */
    private Predicate<Packet> filter;
    /** Whether the action associated to this state is retryable or not. */
    private final boolean retryable;
    /** Timers associated with this entry. */
    private OptionalLong timerId = OptionalLong.empty();

    PeerInteractionState(
        final Consumer<PeerInteractionState> action,
        final Bytes peerId,
        final PacketType expectedType,
        final Predicate<Packet> filter,
        final boolean retryable) {
      this.action = action;
      this.peerId = peerId;
      this.expectedType = expectedType;
      this.filter = filter;
      this.retryable = retryable;
      interactionCounter.labels(expectedType.name()).inc();
      retryCounter = interactionRetryCounter.labels(expectedType.name());
    }

    @Override
    public boolean test(final Packet packet) {
      return expectedType == packet.getType() && (filter == null || filter.test(packet));
    }

    void updateFilter(final Predicate<Packet> filter) {
      this.filter = filter;
    }

    /**
     * Executes the action associated with this state. Sets a "boomerang" timer to itself in case
     * the action is retryable.
     *
     * @param lastTimeout the previous timeout, or 0 if this is the first time the action is being
     *     executed.
     */
    void execute(final long lastTimeout, final int retryCount) {
      action.accept(this);
      if (retryable && retryCount < MAX_RETRIES) {
        final long newTimeout = retryDelayFunction.apply(lastTimeout);
        timerId =
            OptionalLong.of(
                timerUtil.setTimer(
                    newTimeout,
                    () -> {
                      retryCounter.inc();
                      execute(newTimeout, retryCount + 1);
                    }));
      } else {
        inflightInteractions.remove(peerId);
      }
    }

    /** Cancels any timers associated with this entry. */
    void cancelTimers() {
      timerId.ifPresent(timerUtil::cancelTimer);
    }
  }

  public interface AsyncExecutor {
    <T> CompletableFuture<T> execute(Supplier<T> action);
  }

  public static class Builder {
    // Options with default values
    private OutboundMessageHandler outboundMessageHandler = OutboundMessageHandler.NOOP;
    private PeerRequirement peerRequirement = PeerRequirement.NOOP;
    private PeerPermissions peerPermissions = PeerPermissions.noop();
    private long tableRefreshIntervalMs = MILLISECONDS.convert(30, TimeUnit.MINUTES);
    private long cleanPeerTableIntervalMs = MILLISECONDS.convert(1, TimeUnit.MINUTES);
    private final List<DiscoveryPeer> bootstrapNodes = new ArrayList<>();
    private PeerTable peerTable;
    private Subscribers<PeerBondedObserver> peerBondedObservers = Subscribers.create();

    // Required dependencies
    private NodeKey nodeKey;
    private DiscoveryPeer localPeer;
    private TimerUtil timerUtil;
    private AsyncExecutor workerExecutor;
    private MetricsSystem metricsSystem;

    private Builder() {}

    public PeerDiscoveryController build() {
      validate();

      if (peerTable == null) {
        peerTable = new PeerTable(this.nodeKey.getPublicKey().getEncodedBytes(), 16);
      }

      return new PeerDiscoveryController(
          nodeKey,
          localPeer,
          peerTable,
          bootstrapNodes,
          outboundMessageHandler,
          timerUtil,
          workerExecutor,
          tableRefreshIntervalMs,
          cleanPeerTableIntervalMs,
          peerRequirement,
          peerPermissions,
          peerBondedObservers,
          metricsSystem);
    }

    private void validate() {
      validateRequiredDependency(nodeKey, "nodeKey");
      validateRequiredDependency(localPeer, "LocalPeer");
      validateRequiredDependency(timerUtil, "TimerUtil");
      validateRequiredDependency(workerExecutor, "AsyncExecutor");
      validateRequiredDependency(metricsSystem, "MetricsSystem");
      validateRequiredDependency(peerBondedObservers, "PeerBondedObservers");
    }

    private void validateRequiredDependency(final Object object, final String name) {
      checkState(object != null, name + " must be configured.");
    }

    public Builder nodeKey(final NodeKey nodeKey) {
      checkNotNull(nodeKey);
      this.nodeKey = nodeKey;
      return this;
    }

    public Builder localPeer(final DiscoveryPeer localPeer) {
      checkNotNull(localPeer);
      this.localPeer = localPeer;
      return this;
    }

    public Builder peerTable(final PeerTable peerTable) {
      checkNotNull(peerTable);
      this.peerTable = peerTable;
      return this;
    }

    public Builder bootstrapNodes(final Collection<DiscoveryPeer> bootstrapNodes) {
      this.bootstrapNodes.addAll(bootstrapNodes);
      return this;
    }

    public Builder outboundMessageHandler(final OutboundMessageHandler outboundMessageHandler) {
      checkNotNull(outboundMessageHandler);
      this.outboundMessageHandler = outboundMessageHandler;
      return this;
    }

    public Builder timerUtil(final TimerUtil timerUtil) {
      checkNotNull(timerUtil);
      this.timerUtil = timerUtil;
      return this;
    }

    public Builder workerExecutor(final AsyncExecutor workerExecutor) {
      checkNotNull(workerExecutor);
      this.workerExecutor = workerExecutor;
      return this;
    }

    public Builder tableRefreshIntervalMs(final long tableRefreshIntervalMs) {
      checkArgument(tableRefreshIntervalMs >= 0);
      this.tableRefreshIntervalMs = tableRefreshIntervalMs;
      return this;
    }

    public Builder cleanPeerTableIntervalMs(final long cleanPeerTableIntervalMs) {
      checkArgument(cleanPeerTableIntervalMs >= 0);
      this.cleanPeerTableIntervalMs = cleanPeerTableIntervalMs;
      return this;
    }

    public Builder peerRequirement(final PeerRequirement peerRequirement) {
      checkNotNull(peerRequirement);
      this.peerRequirement = peerRequirement;
      return this;
    }

    public Builder peerPermissions(final PeerPermissions peerPermissions) {
      checkNotNull(peerPermissions);
      this.peerPermissions = peerPermissions;
      return this;
    }

    public Builder peerBondedObservers(final Subscribers<PeerBondedObserver> peerBondedObservers) {
      checkNotNull(peerBondedObservers);
      this.peerBondedObservers = peerBondedObservers;
      return this;
    }

    public Builder metricsSystem(final MetricsSystem metricsSystem) {
      checkNotNull(metricsSystem);
      this.metricsSystem = metricsSystem;
      return this;
    }
  }
}
