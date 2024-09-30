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

import org.junit.jupiter.api.Test;

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

    long firstDiscovered;

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);

    DiscoveryPeer p = agent.streamDiscoveredPeers().iterator().next();
    assertThat(p.getFirstDiscovered()).isGreaterThan(0);

    firstDiscovered = p.getFirstDiscovered();

    helper.sendMessageBetweenAgents(testAgent, agent, testAgentPing);

    assertThat(agent.streamDiscoveredPeers()).hasSize(1);

    p = agent.streamDiscoveredPeers().iterator().next();
    assertThat(p.getFirstDiscovered()).isEqualTo(firstDiscovered);
  }
}
