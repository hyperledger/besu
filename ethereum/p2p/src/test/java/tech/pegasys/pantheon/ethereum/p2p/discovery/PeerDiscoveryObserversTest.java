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
package tech.pegasys.pantheon.ethereum.p2p.discovery;

import static io.vertx.core.Vertx.vertx;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static tech.pegasys.pantheon.ethereum.p2p.NetworkingTestHelper.configWithRandomPorts;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryEvent.PeerBondedEvent;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Test;

public class PeerDiscoveryObserversTest extends AbstractPeerDiscoveryTest {
  private static final Logger LOG = LogManager.getLogger();
  private static final int BROADCAST_TCP_PORT = 26422;

  @Test
  public void addAndRemoveObservers() {
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getObserverCount()).isEqualTo(0);

    final long id1 = agent.observePeerBondedEvents((event) -> {});
    final long id2 = agent.observePeerBondedEvents((event) -> {});
    final long id3 = agent.observePeerBondedEvents((event) -> {});
    final long id4 = agent.observePeerDroppedEvents((event) -> {});
    final long id5 = agent.observePeerDroppedEvents((event) -> {});
    final long id6 = agent.observePeerDroppedEvents((event) -> {});
    assertThat(agent.getObserverCount()).isEqualTo(6);

    agent.removePeerBondedObserver(id1);
    agent.removePeerBondedObserver(id2);
    assertThat(agent.getObserverCount()).isEqualTo(4);

    agent.removePeerBondedObserver(id3);
    agent.removePeerDroppedObserver(id4);
    assertThat(agent.getObserverCount()).isEqualTo(2);

    agent.removePeerDroppedObserver(id5);
    agent.removePeerDroppedObserver(id6);
    assertThat(agent.getObserverCount()).isEqualTo(0);

    final long id7 = agent.observePeerBondedEvents((event) -> {});
    final long id8 = agent.observePeerDroppedEvents((event) -> {});
    assertThat(agent.getObserverCount()).isEqualTo(2);

    agent.removePeerBondedObserver(id7);
    agent.removePeerDroppedObserver(id8);
    assertThat(agent.getObserverCount()).isEqualTo(0);
  }

  @Test
  public void removeInexistingObserver() {
    final PeerDiscoveryAgent agent = startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.getObserverCount()).isEqualTo(0);

    agent.observePeerBondedEvents((event) -> {});
    assertThat(agent.removePeerBondedObserver(12345)).isFalse();
  }

  @Test
  public void peerBondedObserverTriggered() throws TimeoutException, InterruptedException {
    // Create 3 discovery agents with no bootstrap peers.
    final List<PeerDiscoveryAgent> others1 = startDiscoveryAgents(3, Collections.emptyList());
    final List<DiscoveryPeer> peers1 =
        others1.stream().map(PeerDiscoveryAgent::getAdvertisedPeer).collect(Collectors.toList());

    // Create two discovery agents pointing to the above as bootstrap peers.
    final List<PeerDiscoveryAgent> others2 = startDiscoveryAgents(2, peers1);
    final List<DiscoveryPeer> peers2 =
        others2.stream().map(PeerDiscoveryAgent::getAdvertisedPeer).collect(Collectors.toList());

    // A list of all peers.
    final List<DiscoveryPeer> allPeers = new ArrayList<>(peers1);
    allPeers.addAll(peers2);

    // Create a discovery agent (which we'll assert on), using the above two peers as bootstrap
    // peers.
    final PeerDiscoveryAgent agent =
        new PeerDiscoveryAgent(
            vertx(),
            SECP256K1.KeyPair.generate(),
            configWithRandomPorts().getDiscovery().setBootstrapPeers(peers2),
            () -> true,
            new PeerBlacklist());

    // A queue for storing peer bonded events.
    final ArrayBlockingQueue<PeerBondedEvent> queue = new ArrayBlockingQueue<>(10);
    agent.observePeerBondedEvents(queue::add);
    assertThatCode(() -> agent.start(BROADCAST_TCP_PORT).get(5, TimeUnit.SECONDS))
        .doesNotThrowAnyException();

    // Wait until we've received 5 events.
    try {
      await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(() -> assertThat(queue.size()).isEqualTo(5));
    } catch (final ConditionTimeoutException | AssertionError e) {
      final List<String> events = new ArrayList<>();
      queue.forEach(evt -> events.add(evt.toString()));
      LOG.error("Queue:\n" + String.join("\n", events), e);
      throw e;
    }
    // Wait one second and check we've received no more events.
    Thread.sleep(1000);
    assertThat(queue.size()).isEqualTo(5);

    // Extract all events and perform asserts on them.
    final List<PeerBondedEvent> events = new ArrayList<>(5);
    queue.drainTo(events, 5);

    assertThat(events)
        .extracting(PeerDiscoveryEvent::getPeer)
        .extracting(DiscoveryPeer::getId)
        .containsExactlyInAnyOrderElementsOf(
            allPeers.stream().map(DiscoveryPeer::getId).collect(Collectors.toList()));
    assertThat(events).extracting(PeerDiscoveryEvent::getTimestamp).isSorted();
  }

  @Test
  public void multiplePeerBondedObserversTriggered() {
    // Create 3 discovery agents with no bootstrap peers.
    final List<PeerDiscoveryAgent> others = startDiscoveryAgents(3, Collections.emptyList());
    final Peer peer = others.stream().map(PeerDiscoveryAgent::getAdvertisedPeer).findFirst().get();

    // Create a discovery agent (which we'll assert on), using the above two peers as bootstrap
    // peers.
    final PeerDiscoveryAgent agent =
        new PeerDiscoveryAgent(
            vertx(),
            SECP256K1.KeyPair.generate(),
            configWithRandomPorts()
                .getDiscovery()
                .setBootstrapPeers(Collections.singletonList(peer)),
            () -> true,
            new PeerBlacklist());

    // Create 5 queues and subscribe them to peer bonded events.
    final List<ArrayBlockingQueue<PeerBondedEvent>> queues =
        Stream.generate(() -> new ArrayBlockingQueue<PeerBondedEvent>(10))
            .limit(5)
            .collect(Collectors.toList());
    queues.forEach(q -> agent.observePeerBondedEvents(q::add));

    // Start the agent and wait until each queue receives one event.
    agent.start(BROADCAST_TCP_PORT);
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(queues).allMatch(q -> q.size() == 1));

    // All events are for the same peer.
    final List<PeerBondedEvent> events =
        queues.stream().map(ArrayBlockingQueue::poll).collect(Collectors.toList());
    assertThat(events).extracting(PeerDiscoveryEvent::getPeer).allMatch(p -> p.equals(peer));

    // We can event check that the event instance is the same across all queues.
    final PeerBondedEvent event = events.get(0);
    assertThat(events).allMatch(e -> e == event);
  }
}
