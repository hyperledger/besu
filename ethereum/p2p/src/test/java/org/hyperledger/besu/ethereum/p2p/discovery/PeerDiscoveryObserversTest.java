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
package org.hyperledger.besu.ethereum.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class PeerDiscoveryObserversTest {
  private static final int BROADCAST_TCP_PORT = 30303;
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void addAndRemoveObservers() {
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getObserverCount()).isEqualTo(0);

    final long id1 = agent.observePeerBondedEvents((event) -> {});
    final long id2 = agent.observePeerBondedEvents((event) -> {});
    final long id3 = agent.observePeerBondedEvents((event) -> {});
    final long id4 = agent.observePeerBondedEvents((event) -> {});
    final long id5 = agent.observePeerBondedEvents((event) -> {});
    final long id6 = agent.observePeerBondedEvents((event) -> {});
    assertThat(agent.getObserverCount()).isEqualTo(6);

    agent.removePeerBondedObserver(id1);
    agent.removePeerBondedObserver(id2);
    assertThat(agent.getObserverCount()).isEqualTo(4);

    agent.removePeerBondedObserver(id3);
    agent.removePeerBondedObserver(id4);
    assertThat(agent.getObserverCount()).isEqualTo(2);

    agent.removePeerBondedObserver(id5);
    agent.removePeerBondedObserver(id6);
    assertThat(agent.getObserverCount()).isEqualTo(0);

    final long id7 = agent.observePeerBondedEvents((event) -> {});
    final long id8 = agent.observePeerBondedEvents((event) -> {});
    assertThat(agent.getObserverCount()).isEqualTo(2);

    agent.removePeerBondedObserver(id7);
    agent.removePeerBondedObserver(id8);
    assertThat(agent.getObserverCount()).isEqualTo(0);
  }

  @Test
  public void removeInexistingObserver() {
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getObserverCount()).isEqualTo(0);

    agent.observePeerBondedEvents((event) -> {});
    assertThat(agent.removePeerBondedObserver(12345)).isFalse();
  }

  @Test
  public void peerBondedObserverTriggered() {
    // Create 3 discovery agents with no bootstrap peers.
    final List<MockPeerDiscoveryAgent> others1 =
        helper.startDiscoveryAgents(3, Collections.emptyList());
    final List<DiscoveryPeer> peers1 =
        others1.stream()
            .map(MockPeerDiscoveryAgent::getAdvertisedPeer)
            .map(Optional::get)
            .collect(Collectors.toList());

    // Create two discovery agents pointing to the above as bootstrap peers.
    final List<MockPeerDiscoveryAgent> others2 = helper.startDiscoveryAgents(2, peers1);
    final List<DiscoveryPeer> peers2 =
        others2.stream()
            .map(MockPeerDiscoveryAgent::getAdvertisedPeer)
            .map(Optional::get)
            .collect(Collectors.toList());

    // A list of all peers.
    final List<DiscoveryPeer> allPeers = new ArrayList<>(peers1);
    allPeers.addAll(peers2);

    // Create a discovery agent (which we'll assert on), using the above two peers as bootstrap
    // peers.
    final MockPeerDiscoveryAgent agent = helper.createDiscoveryAgent(peers2);
    // A queue for storing peer bonded events.
    final List<PeerBondedEvent> events = new ArrayList<>(10);
    agent.observePeerBondedEvents(events::add);
    agent.start(BROADCAST_TCP_PORT).join();

    final HashSet<Bytes> seenPeers = new HashSet<>();
    List<DiscoveryPeer> discoveredPeers =
        events.stream()
            .map(PeerDiscoveryEvent::getPeer)
            // We emit some duplicate events when the tcp port differs (in terms of presence) for a
            // peer,
            // filter peers by id to remove duplicates (See: DefaultPeer::equals).
            // TODO: Should we evaluate peer equality based on id??
            .filter((p) -> seenPeers.add(p.getId()))
            .collect(Collectors.toList());
    assertThat(discoveredPeers.size()).isEqualTo(allPeers.size());

    Assertions.assertThat(discoveredPeers)
        .extracting(DiscoveryPeer::getId)
        .containsExactlyInAnyOrderElementsOf(
            allPeers.stream().map(DiscoveryPeer::getId).collect(Collectors.toList()));
    assertThat(events).extracting(PeerDiscoveryEvent::getTimestamp).isSorted();
  }

  @Test
  public void multiplePeerBondedObserversTriggered() {
    // Create 3 discovery agents with no bootstrap peers.
    final List<MockPeerDiscoveryAgent> others =
        helper.startDiscoveryAgents(3, Collections.emptyList());
    assertThat(others.get(0).getAdvertisedPeer().isPresent()).isTrue();
    final DiscoveryPeer peer = others.get(0).getAdvertisedPeer().get();

    // Create a discovery agent (which we'll assert on), using the above two peers as bootstrap
    // peers.
    final MockPeerDiscoveryAgent agent = helper.createDiscoveryAgent(peer);

    // Create 5 queues and subscribe them to peer bonded events.
    final List<List<PeerBondedEvent>> queues =
        Stream.generate(() -> new ArrayList<PeerBondedEvent>(10))
            .limit(5)
            .collect(Collectors.toList());
    queues.forEach(q -> agent.observePeerBondedEvents(q::add));

    // Start the agent and wait until each queue receives one event.
    agent.start(BROADCAST_TCP_PORT).join();
    for (List<PeerBondedEvent> eventQueue : queues) {
      assertThat(eventQueue.size()).isEqualTo(1);
    }

    // All events are for the same peer.
    final List<PeerBondedEvent> events =
        Stream.of(queues)
            .flatMap(Collection::stream)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    assertThat(events).extracting(PeerDiscoveryEvent::getPeer).allMatch(p -> p.equals(peer));

    // We can event check that the event instance is the same across all queues.
    final PeerBondedEvent event = events.get(0);
    assertThat(events).allMatch(e -> e == event);
  }
}
