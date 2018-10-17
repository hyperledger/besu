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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class TrailingPeerLimiterTest {

  private static final long CHAIN_HEAD = 10_000L;
  private static final int MAX_TRAILING_PEERS = 2;
  private static final int TRAILING_PEER_BLOCKS_BEHIND_THRESHOLD = 10;
  private final EthPeers ethPeers = mock(EthPeers.class);
  private final Blockchain blockchain = mock(Blockchain.class);
  private final List<EthPeer> peers = new ArrayList<>();
  private final TrailingPeerLimiter trailingPeerLimiter =
      new TrailingPeerLimiter(
          ethPeers, blockchain, TRAILING_PEER_BLOCKS_BEHIND_THRESHOLD, MAX_TRAILING_PEERS);

  @Before
  public void setUp() {
    when(ethPeers.availablePeers()).then(invocation -> peers.stream());
    when(blockchain.getChainHeadBlockNumber()).thenReturn(CHAIN_HEAD);
  }

  @Test
  public void shouldDisconnectFurthestBehindPeerWhenTrailingPeerLimitExceeded() {
    final EthPeer ethPeer1 = addPeerWithEstimatedHeight(1);
    addPeerWithEstimatedHeight(3);
    addPeerWithEstimatedHeight(2);

    trailingPeerLimiter.enforceTrailingPeerLimit();

    assertDisconnections(ethPeer1);
  }

  @Test
  public void shouldDisconnectMultiplePeersWhenTrailingPeerLimitExceeded() {
    final EthPeer ethPeer1 = addPeerWithEstimatedHeight(1);
    final EthPeer ethPeer2 = addPeerWithEstimatedHeight(2);
    addPeerWithEstimatedHeight(3);
    addPeerWithEstimatedHeight(4);

    trailingPeerLimiter.enforceTrailingPeerLimit();

    assertDisconnections(ethPeer1, ethPeer2);
  }

  @Test
  public void shouldNotDisconnectPeersWhenLimitNotReached() {
    addPeerWithEstimatedHeight(1);
    addPeerWithEstimatedHeight(2);

    trailingPeerLimiter.enforceTrailingPeerLimit();

    assertDisconnections();
  }

  @Test
  public void shouldNotDisconnectPeersWithinToleranceOfChainHead() {
    addPeerWithEstimatedHeight(CHAIN_HEAD);
    addPeerWithEstimatedHeight(CHAIN_HEAD);
    addPeerWithEstimatedHeight(CHAIN_HEAD);
    addPeerWithEstimatedHeight(CHAIN_HEAD - TRAILING_PEER_BLOCKS_BEHIND_THRESHOLD);
    addPeerWithEstimatedHeight(CHAIN_HEAD - TRAILING_PEER_BLOCKS_BEHIND_THRESHOLD);

    trailingPeerLimiter.enforceTrailingPeerLimit();

    assertDisconnections();
  }

  @Test
  public void shouldRecheckTrailingPeersWhenBlockAddedThatIsMultipleOf100() {
    final EthPeer ethPeer1 = addPeerWithEstimatedHeight(1);
    addPeerWithEstimatedHeight(3);
    addPeerWithEstimatedHeight(2);

    final BlockAddedEvent blockAddedEvent =
        BlockAddedEvent.createForHeadAdvancement(
            new Block(
                new BlockHeaderTestFixture().number(500).buildHeader(),
                new BlockBody(emptyList(), emptyList())));
    trailingPeerLimiter.onBlockAdded(blockAddedEvent, blockchain);

    assertDisconnections(ethPeer1);
  }

  @Test
  public void shouldNotRecheckTrailingPeersWhenBlockAddedIsNotAMultipleOf100() {
    addPeerWithEstimatedHeight(1);
    addPeerWithEstimatedHeight(3);
    addPeerWithEstimatedHeight(2);

    final BlockAddedEvent blockAddedEvent =
        BlockAddedEvent.createForHeadAdvancement(
            new Block(
                new BlockHeaderTestFixture().number(599).buildHeader(),
                new BlockBody(emptyList(), emptyList())));
    trailingPeerLimiter.onBlockAdded(blockAddedEvent, blockchain);

    assertDisconnections();
  }

  private void assertDisconnections(final EthPeer... disconnectedPeers) {
    final List<EthPeer> disconnected = asList(disconnectedPeers);
    for (final EthPeer peer : peers) {
      if (disconnected.contains(peer)) {
        verify(peer).disconnect(DisconnectReason.TOO_MANY_PEERS);
      } else {
        verify(peer, never()).disconnect(any(DisconnectReason.class));
      }
    }
  }

  private EthPeer addPeerWithEstimatedHeight(final long height) {
    final EthPeer peer = mock(EthPeer.class);
    final ChainState chainState = new ChainState();
    chainState.statusReceived(Hash.EMPTY, UInt256.ONE);
    chainState.update(Hash.EMPTY, height);
    when(peer.chainState()).thenReturn(chainState);
    peers.add(peer);
    return peer;
  }
}
