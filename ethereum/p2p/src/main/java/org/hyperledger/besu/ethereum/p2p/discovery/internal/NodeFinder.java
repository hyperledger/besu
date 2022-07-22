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

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeFinder {
  private static final Logger LOG = LoggerFactory.getLogger(NodeFinder.class);
  private Bytes target;

  private final BondingAgent bondingAgent;
  private final FindNeighbourDispatcher findNeighbourDispatcher;
  private boolean iterativeSearchInProgress = false;

  private final TimerUtil timerUtil;
  private Optional<RoundTimeout> currentRoundTimeout = Optional.empty();

  public NavigableMap<Bytes, MetadataPeer> connect = new TreeMap<>();

  public final NavigableMap<Bytes, MetadataPeer> bonded = new TreeMap<>();

  private final NavigableMap<Bytes, MetadataPeer> known = new TreeMap<>();

  private final NavigableMap<Bytes, MetadataPeer> neighbours = new TreeMap<>();

  List<DiscoveryPeer> initialPeers;

  NodeFinder(
      final BondingAgent bondingAgent,
      final TimerUtil timerUtil,
      final FindNeighbourDispatcher neighborFinder) {
    this.bondingAgent = bondingAgent;
    this.timerUtil = timerUtil;
    this.findNeighbourDispatcher = neighborFinder;
  }

  void start(final List<DiscoveryPeer> initialPeers, final Bytes target) {
    if (iterativeSearchInProgress) {
      LOG.info("Skip peer search because previous search is still in progress.");
      return;
    }
    LOG.info("Start peer search.");
    this.target = target;
    this.initialPeers = initialPeers;
    addPeers(initialPeers);
    bondingInitiateRound();
  }

  public void addPeers(final List<DiscoveryPeer> peers) {
    for (final DiscoveryPeer peer : peers) {
      final MetadataPeer iterationParticipant =
          new MetadataPeer(peer, PeerDistanceCalculator.distance(target, peer.getId()));
      known.put(peer.getId(), iterationParticipant);
    }
  }

  private RoundTimeout scheduleTimeout(final Runnable onTimeout) {
    final AtomicBoolean timeoutCancelled = new AtomicBoolean(false);
    final long timerId =
        timerUtil.setTimer(
            TimeUnit.SECONDS.toMillis(2), () -> performIfNotCancelled(onTimeout, timeoutCancelled));
    return new RoundTimeout(timeoutCancelled, timerId);
  }

  private void performIfNotCancelled(final Runnable action, final AtomicBoolean cancelled) {
    if (!cancelled.get()) {
      action.run();
    }
  }

  private void bondingInitiateRound() {
    currentRoundTimeout.ifPresent(RoundTimeout::cancelTimeout);
    List<MetadataPeer> candidates = bondingRoundCandidates();
    if (candidates.isEmpty()) {
      known.putAll(neighbours);
      neighbours.clear();
    }
    candidates = bondingRoundCandidates();
    LOG.info("Initiating bonding round with {} candidates", candidates.size());
    if (!candidates.isEmpty()) {
      iterativeSearchInProgress = true;
    }
    for (final MetadataPeer peer : candidates) {
      peer.bondingStarted();
      bondingAgent.performBonding(peer.getPeer());
    }
    currentRoundTimeout = Optional.of(scheduleTimeout(this::bondingCancelOutstandingRequests));
  }

  private void neighboursInitiateRound() {
    currentRoundTimeout.ifPresent(RoundTimeout::cancelTimeout);
    final List<MetadataPeer> candidates = neighboursRoundCandidates();
    LOG.info(
        "Initiating neighbours round with {} candidates from {} tracked nodes",
        candidates.size(),
        known.size());
    for (final MetadataPeer peer : candidates) {
      peer.findNeighboursStarted();
      findNeighbourDispatcher.findNeighbours(peer.getPeer(), target);
    }
    currentRoundTimeout = Optional.of(scheduleTimeout(this::neighboursCancelOutstandingRequests));
  }

  private void bondingCancelOutstandingRequests() {
    LOG.debug("Bonding round timed out");
    known.clear();
    neighboursInitiateRound();
  }

  private void neighboursCancelOutstandingRequests() {
    LOG.debug("Neighbours round timed out");
    bonded.clear();
    bondingInitiateRound();
  }

  void onNeighboursReceived(final DiscoveryPeer peer, final List<DiscoveryPeer> peers) {
    final MetadataPeer metadataPeer = bonded.get(peer.getId());
    if (metadataPeer == null) {
      return;
    }
    connect.put(peer.getId(), bonded.remove(peer.getId()));
    LOG.info("Received neighbours packet with {} neighbours", peers.size());
    for (final DiscoveryPeer receivedDiscoPeer : peers) {
      final MetadataPeer receivedMetadataPeer =
          new MetadataPeer(
              receivedDiscoPeer,
              PeerDistanceCalculator.distance(target, receivedDiscoPeer.getId()));
      neighbours.put(receivedDiscoPeer.getId(), receivedMetadataPeer);
    }

    if (!metadataPeer.hasOutstandingNeighboursRequest()) {
      return;
    }
    metadataPeer.findNeighboursComplete();
    if (neighboursRoundTermination()) {
      bondingInitiateRound();
    }
  }

  void onBondingComplete(final DiscoveryPeer peer) {
    final MetadataPeer iterationParticipant = known.get(peer.getId());
    if (iterationParticipant == null) {
      return;
    }
    if (!iterationParticipant.hasOutstandingBondRequest()) {
      return;
    }
    bonded.put(peer.getId(), known.remove(peer.getId()));
    iterationParticipant.bondingComplete();
    // System.out.println("bondingRoundTermination() "+bondingRoundTermination()+" "+known.size());
    if (bondingRoundTermination()) {
      neighboursInitiateRound();
    }
  }

  private boolean neighboursRoundTermination() {
    for (final Map.Entry<Bytes, MetadataPeer> entry : bonded.entrySet()) {
      final MetadataPeer metadataPeer = entry.getValue();
      if (metadataPeer.hasOutstandingNeighboursRequest()) {
        return false;
      }
    }
    return true;
  }

  private boolean bondingRoundTermination() {
    for (final Map.Entry<Bytes, MetadataPeer> entry : known.entrySet()) {
      final MetadataPeer metadataPeer = entry.getValue();
      if (metadataPeer.hasOutstandingBondRequest()) {
        return false;
      }
    }
    return true;
  }

  private List<MetadataPeer> bondingRoundCandidates() {
    return known.values().stream()
        .filter(MetadataPeer::isBondingCandidate)
        .collect(Collectors.toList());
  }

  private List<MetadataPeer> neighboursRoundCandidates() {
    return bonded.values().stream()
        .filter(MetadataPeer::isNeighboursRoundCandidate)
        .collect(Collectors.toList());
  }

  @VisibleForTesting
  void cancel() {
    iterativeSearchInProgress = false;
  }

  public static class MetadataPeer implements Comparable<MetadataPeer> {

    DiscoveryPeer peer;
    int distance;

    boolean bondingStarted = false;
    boolean bondingComplete = false;
    boolean findNeighboursStarted = false;
    boolean findNeighboursComplete = false;

    MetadataPeer(final DiscoveryPeer peer, final int distance) {
      this.peer = peer;
      this.distance = distance;
    }

    DiscoveryPeer getPeer() {
      return peer;
    }

    void bondingStarted() {
      this.bondingStarted = true;
    }

    void bondingComplete() {
      this.bondingComplete = true;
    }

    void findNeighboursStarted() {
      this.findNeighboursStarted = true;
    }

    void findNeighboursComplete() {
      this.findNeighboursComplete = true;
    }

    void findNeighboursFailed() {
      this.findNeighboursComplete = true;
    }

    private boolean isBondingCandidate() {
      return !bondingComplete && !bondingStarted && peer.getStatus() == PeerDiscoveryStatus.KNOWN;
    }

    private boolean isNeighboursRoundCandidate() {
      return peer.getStatus() == PeerDiscoveryStatus.BONDED && !findNeighboursStarted;
    }

    private boolean hasOutstandingBondRequest() {
      return bondingStarted && !bondingComplete;
    }

    private boolean hasOutstandingNeighboursRequest() {
      return findNeighboursStarted && !findNeighboursComplete;
    }

    @Override
    public int compareTo(final MetadataPeer o) {
      if (this.distance > o.distance) {
        return 1;
      }
      return -1;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final MetadataPeer that = (MetadataPeer) o;
      return Objects.equals(peer.getId(), that.peer.getId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(peer.getId());
    }

    @Override
    public String toString() {
      return peer + ": " + distance;
    }
  }

  @FunctionalInterface
  public interface FindNeighbourDispatcher {
    /**
     * Sends a FIND_NEIGHBORS message to a {@link DiscoveryPeer}, in search of a target value.
     *
     * @param peer the peer to interrogate
     * @param target the target node ID to find
     */
    void findNeighbours(final DiscoveryPeer peer, final Bytes target);
  }

  @FunctionalInterface
  public interface BondingAgent {
    /**
     * Initiates a bonding PING-PONG cycle with a peer.
     *
     * @param peer The targeted peer.
     */
    void performBonding(final DiscoveryPeer peer);
  }

  private class RoundTimeout {
    private final AtomicBoolean timeoutCancelled;
    private final long timerId;

    private RoundTimeout(final AtomicBoolean timeoutCancelled, final long timerId) {
      this.timeoutCancelled = timeoutCancelled;
      this.timerId = timerId;
    }

    public void cancelTimeout() {
      timerUtil.cancelTimer(timerId);
      timeoutCancelled.set(true);
    }
  }
}
