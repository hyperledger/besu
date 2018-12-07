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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerDistanceCalculator.distance;

import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;

class RecursivePeerRefreshState {
  private final int CONCURRENT_REQUEST_LIMIT = 3;
  private final BytesValue target;
  private final PeerBlacklist peerBlacklist;
  private final BondingAgent bondingAgent;
  private final NeighborFinder neighborFinder;
  private final List<PeerDistance> anteList;
  private final List<OutstandingRequest> outstandingRequestList;
  private final List<BytesValue> contactedInCurrentExecution;

  RecursivePeerRefreshState(
      final BytesValue target,
      final PeerBlacklist peerBlacklist,
      final BondingAgent bondingAgent,
      final NeighborFinder neighborFinder) {
    this.target = target;
    this.peerBlacklist = peerBlacklist;
    this.bondingAgent = bondingAgent;
    this.neighborFinder = neighborFinder;
    this.anteList = new ArrayList<>();
    this.outstandingRequestList = new ArrayList<>();
    this.contactedInCurrentExecution = new ArrayList<>();
  }

  void kickstartBootstrapPeers(final List<Peer> bootstrapPeers) {
    for (Peer bootstrapPeer : bootstrapPeers) {
      final BytesValue peerId = bootstrapPeer.getId();
      outstandingRequestList.add(new OutstandingRequest(bootstrapPeer));
      contactedInCurrentExecution.add(peerId);
      bondingAgent.performBonding(bootstrapPeer, true);
      neighborFinder.issueFindNodeRequest(bootstrapPeer, target);
    }
  }

  /**
   * This method is intended to be called periodically by the {@link PeerDiscoveryController}, which
   * will maintain a timer for purposes of effecting expiration of requests outstanding. Requests
   * once encountered are deemed eligible for eviction if they have not been dispatched before the
   * next invocation of the method.
   */
  public void executeTimeoutEvaluation() {
    for (int i = 0; i < outstandingRequestList.size(); i++) {
      if (outstandingRequestList.get(i).getEvaluation()) {
        final List<DiscoveryPeer> queryCandidates = determineFindNodeCandidates(anteList.size());
        for (DiscoveryPeer candidate : queryCandidates) {
          if (!contactedInCurrentExecution.contains(candidate.getId())
              && !outstandingRequestList.contains(new OutstandingRequest(candidate))) {
            outstandingRequestList.remove(i);
            executeFindNodeRequest(candidate);
          }
        }
      }
      outstandingRequestList.get(i).setEvaluation();
    }
  }

  private void executeFindNodeRequest(final DiscoveryPeer peer) {
    final BytesValue peerId = peer.getId();
    outstandingRequestList.add(new OutstandingRequest(peer));
    contactedInCurrentExecution.add(peerId);
    neighborFinder.issueFindNodeRequest(peer, target);
  }

  /**
   * The lookup initiator starts by picking CONCURRENT_REQUEST_LIMIT closest nodes to the target it
   * knows of. The initiator then issues concurrent FindNode packets to those nodes.
   */
  private void initiatePeerRefreshCycle(final List<DiscoveryPeer> peers) {
    for (DiscoveryPeer peer : peers) {
      if (!contactedInCurrentExecution.contains(peer.getId())) {
        executeFindNodeRequest(peer);
      }
    }
  }

  void onNeighboursPacketReceived(final NeighborsPacketData neighboursPacket, final Peer peer) {
    if (outstandingRequestList.contains(new OutstandingRequest(peer))) {
      final List<DiscoveryPeer> receivedPeerList = neighboursPacket.getNodes();
      for (DiscoveryPeer receivedPeer : receivedPeerList) {
        if (!peerBlacklist.contains(receivedPeer)) {
          bondingAgent.performBonding(receivedPeer, false);
          anteList.add(new PeerDistance(receivedPeer, distance(target, receivedPeer.getId())));
        }
      }
      outstandingRequestList.remove(new OutstandingRequest(peer));
      queryNearestNodes();
    }
  }

  private List<DiscoveryPeer> determineFindNodeCandidates(final int threshold) {
    anteList.sort(
        (peer1, peer2) -> {
          if (peer1.getDistance() > peer2.getDistance()) return 1;
          if (peer1.getDistance() < peer2.getDistance()) return -1;
          return 0;
        });
    return anteList.subList(0, threshold).stream().map(PeerDistance::getPeer).collect(toList());
  }

  private void queryNearestNodes() {
    if (outstandingRequestList.isEmpty()) {
      final List<DiscoveryPeer> queryCandidates =
          determineFindNodeCandidates(CONCURRENT_REQUEST_LIMIT);
      initiatePeerRefreshCycle(queryCandidates);
    }
  }

  @VisibleForTesting
  List<OutstandingRequest> getOutstandingRequestList() {
    return outstandingRequestList;
  }

  static class PeerDistance {
    DiscoveryPeer peer;
    Integer distance;

    PeerDistance(final DiscoveryPeer peer, final Integer distance) {
      this.peer = peer;
      this.distance = distance;
    }

    DiscoveryPeer getPeer() {
      return peer;
    }

    Integer getDistance() {
      return distance;
    }

    @Override
    public String toString() {
      return peer + ": " + distance;
    }
  }

  static class OutstandingRequest {
    boolean evaluation;
    Peer peer;

    OutstandingRequest(final Peer peer) {
      this.evaluation = false;
      this.peer = peer;
    }

    boolean getEvaluation() {
      return evaluation;
    }

    Peer getPeer() {
      return peer;
    }

    void setEvaluation() {
      this.evaluation = true;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final OutstandingRequest that = (OutstandingRequest) o;
      return Objects.equals(peer.getId(), that.peer.getId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(peer.getId());
    }

    @Override
    public String toString() {
      return peer.toString();
    }
  }

  public interface NeighborFinder {
    /**
     * Sends a FIND_NEIGHBORS message to a {@link DiscoveryPeer}, in search of a target value.
     *
     * @param peer the peer to interrogate
     * @param target the target node ID to find
     */
    void issueFindNodeRequest(final Peer peer, final BytesValue target);
  }

  public interface BondingAgent {
    /**
     * Initiates a bonding PING-PONG cycle with a peer.
     *
     * @param peer The targeted peer.
     * @param bootstrap Whether this is a bootstrap interaction.
     */
    void performBonding(final Peer peer, final boolean bootstrap);
  }
}
