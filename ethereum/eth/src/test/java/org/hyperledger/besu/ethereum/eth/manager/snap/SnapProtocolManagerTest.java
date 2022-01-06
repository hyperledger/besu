/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.snap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection.PeerSendHandler;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import kotlin.collections.ArrayDeque;
import org.junit.Before;
import org.junit.Test;

public final class SnapProtocolManagerTest {

  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final BonsaiPersistedWorldState mutableWorldState = mock(BonsaiPersistedWorldState.class);
  private final BonsaiWorldStateKeyValueStorage worldStateStorage =
      mock(BonsaiWorldStateKeyValueStorage.class);
  private final EthMessages ethMessages = new EthMessages();

  @Before
  public void setUp() {
    new SnapServer(ethMessages, worldStateArchive);
    when(worldStateArchive.getMutable()).thenReturn(mutableWorldState);
    when(mutableWorldState.getWorldStateStorage()).thenReturn(worldStateStorage);
  }

  public static SnapProtocolManager create(final WorldStateArchive worldStateArchive) {
    EthPeers peers = new EthPeers(EthProtocol.NAME, TestClock.fixed(), new NoOpMetricsSystem());
    EthMessages snapMessages = new EthMessages();
    return new SnapProtocolManager(Collections.emptyList(), peers, snapMessages, worldStateArchive);
  }

  private MockPeerConnection setupPeerWithoutStatusExchange(
      final SnapProtocolManager snapManager, final PeerSendHandler onSend) {
    final Set<Capability> caps = new HashSet<>(Collections.singletonList(SnapProtocol.SNAP1));
    final MockPeerConnection peer = new MockPeerConnection(caps, onSend);
    snapManager.handleNewConnection(peer);
    return peer;
  }

  @Test
  public void disconnectOnUnsolicitedMessage() {
    try (final SnapProtocolManager snapManager = create(worldStateArchive)) {
      final MessageData messageData =
          AccountRangeMessage.create(new TreeMap<>(), new ArrayDeque<>())
              .wrapMessageData(BigInteger.ONE);
      final MockPeerConnection peer =
          setupPeerWithoutStatusExchange(snapManager, (cap, msg, conn) -> {});
      assertThat(peer.isDisconnected()).isFalse();
      snapManager.processMessage(SnapProtocol.SNAP1, new DefaultMessage(peer, messageData));
      assertThat(peer.isDisconnected()).isTrue();
    }
  }
}
