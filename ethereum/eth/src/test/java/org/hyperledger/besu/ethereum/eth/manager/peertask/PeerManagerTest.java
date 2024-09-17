/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import java.time.Clock;
import java.util.Collections;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PeerManagerTest {

  public PeerManager peerManager;

  @BeforeEach
  public void beforeTest() {
    peerManager = new PeerManager();
  }

  @Test
  public void testGetPeer() throws NoAvailablePeerException {
    EthPeer protocol1With5ReputationPeer =
        createTestPeer(Set.of(Capability.create("capability1", 1)), "protocol1", 5);
    peerManager.addPeer(protocol1With5ReputationPeer);
    EthPeer protocol1With4ReputationPeer =
        createTestPeer(Set.of(Capability.create("capability1", 1)), "protocol1", 4);
    peerManager.addPeer(protocol1With4ReputationPeer);
    EthPeer protocol2With50ReputationPeer =
        createTestPeer(Set.of(Capability.create("capability1", 1)), "protocol2", 50);
    peerManager.addPeer(protocol2With50ReputationPeer);
    EthPeer protocol2With4ReputationPeer =
        createTestPeer(Set.of(Capability.create("capability1", 1)), "protocol2", 4);
    peerManager.addPeer(protocol2With4ReputationPeer);

    EthPeer result = peerManager.getPeer((p) -> p.getProtocolName().equals("protocol1"));

    Assertions.assertSame(protocol1With5ReputationPeer, result);
  }

  private EthPeer createTestPeer(
      final Set<Capability> connectionCapabilities,
      final String protocolName,
      final int reputationAdjustment) {
    PeerConnection peerConnection = new MockPeerConnection(connectionCapabilities);
    EthPeer peer =
        new EthPeer(
            peerConnection,
            protocolName,
            null,
            Collections.emptyList(),
            1,
            Clock.systemUTC(),
            Collections.emptyList(),
            Bytes.EMPTY);
    for (int i = 0; i < reputationAdjustment; i++) {
      peer.getReputation().recordUsefulResponse();
    }
    return peer;
  }
}
