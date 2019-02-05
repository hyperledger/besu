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
package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerTable.AddResult.Outcome;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryStatus;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 *       received after having sent a PING or a PING has been received and a PONG has been sent in
 *       response). This is the same as having an "active" channel.
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

  private static final Logger LOG = LogManager.getLogger();
  private static final long REFRESH_CHECK_INTERVAL_MILLIS = MILLISECONDS.convert(30, SECONDS);
  protected final TimerUtil timerUtil;
  private final PeerTable peerTable;

  private final Collection<DiscoveryPeer> bootstrapNodes;

  /* A tracker for inflight interactions and the state machine of a peer. */
  private final Map<BytesValue, PeerInteractionState> inflightInteractions =
      new ConcurrentHashMap<>();

  private final AtomicBoolean started = new AtomicBoolean(false);

  private final SECP256K1.KeyPair keypair;
  // The peer representation of this node
  private final DiscoveryPeer localPeer;
  private final OutboundMessageHandler outboundMessageHandler;
  private final PeerBlacklist peerBlacklist;
  private final Optional<NodeWhitelistController> nodeWhitelistController;

  private RetryDelayFunction retryDelayFunction = RetryDelayFunction.linear(1.5, 2000, 60000);

  private final long tableRefreshIntervalMs;

  private final PeerRequirement peerRequirement;

  private long lastRefreshTime = -1;

  private OptionalLong tableRefreshTimerId = OptionalLong.empty();

  // Observers for "peer bonded" discovery events.
  private final Subscribers<Consumer<PeerBondedEvent>> peerBondedObservers;

  public PeerDiscoveryController(
      final KeyPair keypair,
      final DiscoveryPeer localPeer,
      final PeerTable peerTable,
      final Collection<DiscoveryPeer> bootstrapNodes,
      final OutboundMessageHandler outboundMessageHandler,
      final TimerUtil timerUtil,
      final long tableRefreshIntervalMs,
      final PeerRequirement peerRequirement,
      final PeerBlacklist peerBlacklist,
      final Optional<NodeWhitelistController> nodeWhitelistController,
      final Subscribers<Consumer<PeerBondedEvent>> peerBondedObservers) {
    this.timerUtil = timerUtil;
    this.keypair = keypair;
    this.localPeer = localPeer;
    this.bootstrapNodes = bootstrapNodes;
    this.peerTable = peerTable;
    this.tableRefreshIntervalMs = tableRefreshIntervalMs;
    this.peerRequirement = peerRequirement;
    this.peerBlacklist = peerBlacklist;
    this.nodeWhitelistController = nodeWhitelistController;
    this.outboundMessageHandler = outboundMessageHandler;
    this.peerBondedObservers = peerBondedObservers;
  }

  public CompletableFuture<?> start() {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("The peer table had already been started");
    }

    bootstrapNodes
        .stream()
        .filter(node -> peerTable.tryAdd(node).getOutcome() == Outcome.ADDED)
        .filter(node -> whitelistIfPresentIsNodePermitted(node))
        .forEach(node -> bond(node, true));

    final long timerId =
        timerUtil.setPeriodic(
            Math.min(REFRESH_CHECK_INTERVAL_MILLIS, tableRefreshIntervalMs),
            () -> refreshTableIfRequired());
    tableRefreshTimerId = OptionalLong.of(timerId);

    return CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<?> stop() {
    if (!started.compareAndSet(true, false)) {
      return CompletableFuture.completedFuture(null);
    }

    tableRefreshTimerId.ifPresent(timerUtil::cancelTimer);
    tableRefreshTimerId = OptionalLong.empty();
    inflightInteractions.values().forEach(PeerInteractionState::cancelTimers);
    inflightInteractions.clear();
    return CompletableFuture.completedFuture(null);
  }

  private boolean whitelistIfPresentIsNodePermitted(final DiscoveryPeer sender) {
    return nodeWhitelistController
        .map(nodeWhitelistController1 -> nodeWhitelistController1.isPermitted(sender))
        .orElse(true);
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
    LOG.trace(
        "<<< Received {} discovery packet from {} ({}): {}",
        packet.getType(),
        sender.getEndpoint(),
        sender.getId().slice(0, 16),
        packet);

    // Message from self. This should not happen.
    if (sender.getId().equals(localPeer.getId())) {
      return;
    }

    if (!whitelistIfPresentIsNodePermitted(sender)) {
      LOG.trace("Dropping packet from peer not in the whitelist ({})", sender.getEnodeURI());
      return;
    }

    // Load the peer from the table, or use the instance that comes in.
    final Optional<DiscoveryPeer> maybeKnownPeer = peerTable.get(sender);
    final DiscoveryPeer peer = maybeKnownPeer.orElse(sender);
    final boolean peerKnown = maybeKnownPeer.isPresent();
    final boolean peerBlacklisted = peerBlacklist.contains(peer);

    switch (packet.getType()) {
      case PING:
        LOG.trace("Received PING packet from {}", sender.getEnodeURI());
        if (!peerBlacklisted && addToPeerTable(peer)) {
          final PingPacketData ping = packet.getPacketData(PingPacketData.class).get();
          respondToPing(ping, packet.getHash(), peer);
        }

        break;
      case PONG:
        {
          LOG.trace("Received PONG packet from {}", sender.getEnodeURI());
          matchInteraction(packet)
              .ifPresent(
                  interaction -> {
                    if (peerBlacklisted) {
                      return;
                    }
                    addToPeerTable(peer);

                    // If this was a bootstrap peer, let's ask it for nodes near to us.
                    if (interaction.isBootstrap()) {
                      findNodes(peer, localPeer.getId());
                    }
                  });
          break;
        }
      case NEIGHBORS:
        LOG.trace("Received NEIGHBORS packet from {}", sender.getEnodeURI());
        matchInteraction(packet)
            .ifPresent(
                interaction -> {
                  // Extract the peers from the incoming packet.
                  final List<DiscoveryPeer> neighbors =
                      packet
                          .getPacketData(NeighborsPacketData.class)
                          .map(NeighborsPacketData::getNodes)
                          .orElse(emptyList());

                  for (final DiscoveryPeer neighbor : neighbors) {
                    // If the peer is not whitelisted, is blacklisted, is already known, or
                    // represents this node, skip bonding
                    if (!whitelistIfPresentIsNodePermitted(neighbor)
                        || peerBlacklist.contains(neighbor)
                        || peerTable.get(neighbor).isPresent()
                        || neighbor.getId().equals(localPeer.getId())) {
                      continue;
                    }
                    bond(neighbor, false);
                  }
                });
        break;

      case FIND_NEIGHBORS:
        LOG.trace("Received FIND_NEIGHBORS packet from {}", sender.getEnodeURI());
        if (!peerKnown || peerBlacklisted) {
          break;
        }
        final FindNeighborsPacketData fn =
            packet.getPacketData(FindNeighborsPacketData.class).get();
        respondToFindNeighbors(fn, peer);
        break;
    }
  }

  private boolean addToPeerTable(final DiscoveryPeer peer) {
    final PeerTable.AddResult result = peerTable.tryAdd(peer);
    if (result.getOutcome() == Outcome.SELF) {
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

    if (result.getOutcome() == Outcome.ALREADY_EXISTED) {
      // Bump peer.
      peerTable.evict(peer);
      peerTable.tryAdd(peer);
    } else if (result.getOutcome() == Outcome.BUCKET_FULL) {
      peerTable.evict(result.getEvictionCandidate());
      peerTable.tryAdd(peer);
    }

    return true;
  }

  private void notifyPeerBonded(final DiscoveryPeer peer, final long now) {
    final PeerBondedEvent event = new PeerBondedEvent(peer, now);
    dispatchEvent(peerBondedObservers, event);
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
      LOG.info("Peer table refresh triggered by timer expiry");
      refreshTable();
    } else if (!peerRequirement.hasSufficientPeers()) {
      LOG.info("Peer table refresh triggered by insufficient peers");
      refreshTable();
    }
  }

  /**
   * Refreshes the peer table by generating a random ID and interrogating the closest nodes for it.
   * Currently the refresh process is NOT recursive.
   */
  private void refreshTable() {
    final BytesValue target = Peer.randomId();
    peerTable.nearestPeers(Peer.randomId(), 16).forEach((peer) -> findNodes(peer, target));
    lastRefreshTime = System.currentTimeMillis();
  }

  /**
   * Initiates a bonding PING-PONG cycle with a peer.
   *
   * @param peer The targeted peer.
   * @param bootstrap Whether this is a bootstrap interaction.
   */
  @VisibleForTesting
  void bond(final DiscoveryPeer peer, final boolean bootstrap) {
    peer.setFirstDiscovered(System.currentTimeMillis());
    peer.setStatus(PeerDiscoveryStatus.BONDING);

    final Consumer<PeerInteractionState> action =
        interaction -> {
          final PingPacketData data =
              PingPacketData.create(localPeer.getEndpoint(), peer.getEndpoint());
          final Packet pingPacket = createPacket(PacketType.PING, data);

          final BytesValue pingHash = pingPacket.getHash();
          // Update the matching filter to only accept the PONG if it echoes the hash of our PING.
          final Predicate<Packet> newFilter =
              packet ->
                  packet
                      .getPacketData(PongPacketData.class)
                      .map(pong -> pong.getPingHash().equals(pingHash))
                      .orElse(false);
          interaction.updateFilter(newFilter);

          sendPacket(peer, pingPacket);
        };

    // The filter condition will be updated as soon as the action is performed.
    final PeerInteractionState ping =
        new PeerInteractionState(action, PacketType.PONG, (packet) -> false, true, bootstrap);
    dispatchInteraction(peer, ping);
  }

  private void sendPacket(final DiscoveryPeer peer, final PacketType type, final PacketData data) {
    Packet packet = createPacket(type, data);
    outboundMessageHandler.send(peer, packet);
  }

  private void sendPacket(final DiscoveryPeer peer, final Packet packet) {
    outboundMessageHandler.send(peer, packet);
  }

  @VisibleForTesting
  Packet createPacket(final PacketType type, final PacketData data) {
    return Packet.create(type, data, keypair);
  }

  /**
   * Sends a FIND_NEIGHBORS message to a {@link DiscoveryPeer}, in search of a target value.
   *
   * @param peer the peer to interrogate
   * @param target the target node ID to find
   */
  private void findNodes(final DiscoveryPeer peer, final BytesValue target) {
    final Consumer<PeerInteractionState> action =
        (interaction) -> {
          final FindNeighborsPacketData data = FindNeighborsPacketData.create(target);
          sendPacket(peer, PacketType.FIND_NEIGHBORS, data);
        };
    final PeerInteractionState interaction =
        new PeerInteractionState(action, PacketType.NEIGHBORS, packet -> true, true, false);
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
    state.execute(0);
  }

  private void respondToPing(
      final PingPacketData packetData, final BytesValue pingHash, final DiscoveryPeer sender) {
    final PongPacketData data = PongPacketData.create(packetData.getFrom(), pingHash);
    sendPacket(sender, PacketType.PONG, data);
  }

  private void respondToFindNeighbors(
      final FindNeighborsPacketData packetData, final DiscoveryPeer sender) {
    // TODO: for now return 16 peers. Other implementations calculate how many
    // peers they can fit in a 1280-byte payload.
    final List<DiscoveryPeer> peers = peerTable.nearestPeers(packetData.getTarget(), 16);
    final PacketData data = NeighborsPacketData.create(peers);
    sendPacket(sender, PacketType.NEIGHBORS, data);
  }

  // Dispatches an event to a set of observers.
  private <T extends PeerDiscoveryEvent> void dispatchEvent(
      final Subscribers<Consumer<T>> observers, final T event) {
    observers.forEach(observer -> observer.accept(event));
  }

  /**
   * Returns a copy of the known peers. Modifications to the list will not update the table's state,
   * but modifications to the Peers themselves will.
   *
   * @return List of peers.
   */
  public Collection<DiscoveryPeer> getPeers() {
    return peerTable.getAllPeers();
  }

  public void setRetryDelayFunction(final RetryDelayFunction retryDelayFunction) {
    this.retryDelayFunction = retryDelayFunction;
  }

  /** Holds the state machine data for a peer interaction. */
  private class PeerInteractionState implements Predicate<Packet> {
    /**
     * The action that led to the peer being in this state (e.g. sending a PING or NEIGHBORS
     * message), in case it needs to be retried.
     */
    private final Consumer<PeerInteractionState> action;
    /** The expected type of the message that will transition the peer out of this state. */
    private final PacketType expectedType;
    /** A custom filter to accept transitions out of this state. */
    private Predicate<Packet> filter;
    /** Whether the action associated to this state is retryable or not. */
    private final boolean retryable;
    /** Whether this is an entry for a bootstrap peer. */
    private final boolean bootstrap;
    /** Timers associated with this entry. */
    private OptionalLong timerId = OptionalLong.empty();

    PeerInteractionState(
        final Consumer<PeerInteractionState> action,
        final PacketType expectedType,
        final Predicate<Packet> filter,
        final boolean retryable,
        final boolean bootstrap) {
      this.action = action;
      this.expectedType = expectedType;
      this.filter = filter;
      this.retryable = retryable;
      this.bootstrap = bootstrap;
    }

    @Override
    public boolean test(final Packet packet) {
      return expectedType == packet.getType() && (filter == null || filter.test(packet));
    }

    void updateFilter(final Predicate<Packet> filter) {
      this.filter = filter;
    }

    boolean isBootstrap() {
      return bootstrap;
    }

    /**
     * Executes the action associated with this state. Sets a "boomerang" timer to itself in case
     * the action is retryable.
     *
     * @param lastTimeout the previous timeout, or 0 if this is the first time the action is being
     *     executed.
     */
    void execute(final long lastTimeout) {
      action.accept(this);
      if (retryable) {
        final long newTimeout = retryDelayFunction.apply(lastTimeout);
        timerId = OptionalLong.of(timerUtil.setTimer(newTimeout, () -> execute(newTimeout)));
      }
    }

    /** Cancels any timers associated with this entry. */
    void cancelTimers() {
      timerId.ifPresent(timerUtil::cancelTimer);
    }
  }
}
