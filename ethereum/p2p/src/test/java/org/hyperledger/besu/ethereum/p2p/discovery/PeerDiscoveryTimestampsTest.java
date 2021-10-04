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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.MockPeerDiscoveryAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.Packet;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class PeerDiscoveryTimestampsTest {
  private final PeerDiscoveryTestHelper helper = new PeerDiscoveryTestHelper();

  @Test
  public void lastSeenAndFirstDiscoveredTimestampsUpdatedOnMessage() {
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    final MockPeerDiscoveryAgent testAgent = helper.startDiscoveryAgent();
    final Packet testAgentPing = helper.createPingPacket(testAgent, agent);
    helper.sendMessageBetweenAgents(testAgent, agent, testAgentPing);

    final Packet agentPing = helper.createPingPacket(agent, testAgent);
    helper.sendMessageBetweenAgents(agent, testAgent, agentPing);

    final Packet pong = helper.createPongPacket(agent, Hash.hash(agentPing.getHash()));
    helper.sendMessageBetweenAgents(testAgent, agent, pong);

    final AtomicLong lastSeen = new AtomicLong();
    final AtomicLong firstDiscovered = new AtomicLong();

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);

    DiscoveryPeer p = agent.streamDiscoveredPeers().iterator().next();
    assertThat(p.getLastSeen()).isGreaterThan(0);
    assertThat(p.getFirstDiscovered()).isGreaterThan(0);

    lastSeen.set(p.getLastSeen());
    firstDiscovered.set(p.getFirstDiscovered());

    helper.sendMessageBetweenAgents(testAgent, agent, testAgentPing);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);

    p = agent.streamDiscoveredPeers().iterator().next();
    assertThat(p.getLastSeen()).isGreaterThan(lastSeen.get());
    assertThat(p.getFirstDiscovered()).isEqualTo(firstDiscovered.get());
  }

  @Test
  public void lastContactedTimestampUpdatedOnOutboundMessage() {
    final MockPeerDiscoveryAgent agent = helper.startDiscoveryAgent(Collections.emptyList());
    assertThat(agent.streamDiscoveredPeers()).hasSize(0);

    // Start a test peer and send a PING packet to the agent under test.
    final MockPeerDiscoveryAgent testAgent = helper.startDiscoveryAgent();
    final Packet ping = helper.createPingPacket(testAgent, agent);
    helper.sendMessageBetweenAgents(testAgent, agent, ping);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);

    final AtomicLong lastContacted = new AtomicLong();
    final AtomicLong lastSeen = new AtomicLong();
    final AtomicLong firstDiscovered = new AtomicLong();

    DiscoveryPeer peer = agent.streamDiscoveredPeers().iterator().next();
    final long lc = peer.getLastContacted();
    final long ls = peer.getLastSeen();
    final long fd = peer.getFirstDiscovered();

    assertThat(lc).isGreaterThan(0);
    assertThat(ls).isGreaterThan(0);
    assertThat(fd).isGreaterThan(0);

    lastContacted.set(lc);
    lastSeen.set(ls);
    firstDiscovered.set(fd);

    // Send another packet and ensure that timestamps are updated accordingly.
    // Sleep beforehand to make sure timestamps will be different.
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      // Swallow exception because we only want to pause the test.
    }
    helper.sendMessageBetweenAgents(testAgent, agent, ping);

    peer = agent.streamDiscoveredPeers().iterator().next();

    assertThat(peer.getLastContacted()).isGreaterThan(lastContacted.get());
    assertThat(peer.getLastSeen()).isGreaterThan(lastSeen.get());
    assertThat(peer.getFirstDiscovered()).isEqualTo(firstDiscovered.get());
  }
}
