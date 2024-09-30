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

import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.PeerReputation;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DefaultPeerSelectorTest {

  public DefaultPeerSelector peerSelector;

  @BeforeEach
  public void beforeTest() {
    ProtocolSpec protocolSpec = Mockito.mock(ProtocolSpec.class);
    Mockito.when(protocolSpec.isPoS()).thenReturn(false);
    peerSelector = new DefaultPeerSelector(() -> protocolSpec);
  }

  @Test
  public void testGetPeer() throws NoAvailablePeerException {
    EthPeer expectedPeer = createTestPeer(10, EthProtocol.get(), 5);
    peerSelector.addPeer(expectedPeer);
    EthPeer excludedForLowChainHeightPeer = createTestPeer(5, EthProtocol.get(), 50);
    peerSelector.addPeer(excludedForLowChainHeightPeer);
    EthPeer excludedForWrongProtocolPeer = createTestPeer(10, SnapProtocol.get(), 50);
    peerSelector.addPeer(excludedForWrongProtocolPeer);
    EthPeer excludedForLowReputationPeer = createTestPeer(10, EthProtocol.get(), 1);
    peerSelector.addPeer(excludedForLowReputationPeer);
    EthPeer excludedForBeingAlreadyUsedPeer = createTestPeer(10, EthProtocol.get(), 50);
    peerSelector.addPeer(excludedForBeingAlreadyUsedPeer);

    Set<EthPeer> usedEthPeers = new HashSet<>();
    usedEthPeers.add(excludedForBeingAlreadyUsedPeer);

    EthPeer result = peerSelector.getPeer(usedEthPeers, 10, EthProtocol.get());

    Assertions.assertSame(expectedPeer, result);
  }

  @Test
  public void testGetPeerButNoPeerMatchesFilter() {
    EthPeer expectedPeer = createTestPeer(10, EthProtocol.get(), 5);
    peerSelector.addPeer(expectedPeer);
    EthPeer excludedForLowChainHeightPeer = createTestPeer(5, EthProtocol.get(), 50);
    peerSelector.addPeer(excludedForLowChainHeightPeer);
    EthPeer excludedForWrongProtocolPeer = createTestPeer(10, SnapProtocol.get(), 50);
    peerSelector.addPeer(excludedForWrongProtocolPeer);
    EthPeer excludedForLowReputationPeer = createTestPeer(10, EthProtocol.get(), 1);
    peerSelector.addPeer(excludedForLowReputationPeer);
    EthPeer excludedForBeingAlreadyUsedPeer = createTestPeer(10, EthProtocol.get(), 50);
    peerSelector.addPeer(excludedForBeingAlreadyUsedPeer);

    Set<EthPeer> usedEthPeers = new HashSet<>();
    usedEthPeers.add(excludedForBeingAlreadyUsedPeer);

    Assertions.assertThrows(
        NoAvailablePeerException.class,
        () -> peerSelector.getPeer(usedEthPeers, 10, new MockSubProtocol()));
  }

  private EthPeer createTestPeer(
      final long chainHeight, final SubProtocol protocol, final int reputation) {
    EthPeer ethPeer = Mockito.mock(EthPeer.class);
    PeerConnection peerConnection = Mockito.mock(PeerConnection.class);
    Peer peer = Mockito.mock(Peer.class);
    ChainState chainState = Mockito.mock(ChainState.class);
    PeerReputation peerReputation = Mockito.mock(PeerReputation.class);

    Mockito.when(ethPeer.getConnection()).thenReturn(peerConnection);
    Mockito.when(peerConnection.getPeer()).thenReturn(peer);
    Mockito.when(ethPeer.getProtocolName()).thenReturn(protocol.getName());
    Mockito.when(ethPeer.chainState()).thenReturn(chainState);
    Mockito.when(chainState.getEstimatedHeight()).thenReturn(chainHeight);
    Mockito.when(ethPeer.getReputation()).thenReturn(peerReputation);
    Mockito.when(peerReputation.getScore()).thenReturn(reputation);

    Mockito.when(ethPeer.compareTo(Mockito.any(EthPeer.class)))
        .thenAnswer(
            (invocationOnMock) -> {
              EthPeer otherPeer = invocationOnMock.getArgument(0, EthPeer.class);
              return Integer.compare(reputation, otherPeer.getReputation().getScore());
            });
    return ethPeer;
  }

  private static class MockSubProtocol implements SubProtocol {

    @Override
    public String getName() {
      return "Mock";
    }

    @Override
    public int messageSpace(final int protocolVersion) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValidMessageCode(final int protocolVersion, final int code) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String messageName(final int protocolVersion, final int code) {
      throw new UnsupportedOperationException();
    }
  }
}
