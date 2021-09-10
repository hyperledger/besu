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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.discovery.PeerDiscoveryStatus;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.RecursivePeerRefreshState.BondingAgent;
import org.hyperledger.besu.ethereum.p2p.discovery.internal.RecursivePeerRefreshState.FindNeighbourDispatcher;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;

import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class RecursivePeerRefreshStateTest {
  private static final Bytes TARGET = createId(0);
  private final PeerDiscoveryPermissions peerPermissions = mock(PeerDiscoveryPermissions.class);
  private final BondingAgent bondingAgent = mock(BondingAgent.class);
  private final FindNeighbourDispatcher neighborFinder = mock(FindNeighbourDispatcher.class);
  private final MockTimerUtil timerUtil = new MockTimerUtil();

  private final DiscoveryPeer localPeer = createPeer(9, "127.0.0.9", 9, 9);
  private final DiscoveryPeer peer1 = createPeer(1, "127.0.0.1", 1, 1);
  private final DiscoveryPeer peer2 = createPeer(2, "127.0.0.2", 2, 2);
  private final DiscoveryPeer peer3 = createPeer(3, "127.0.0.3", 3, 3);
  private final DiscoveryPeer peer4 = createPeer(4, "127.0.0.3", 4, 4);

  private RecursivePeerRefreshState recursivePeerRefreshState =
      new RecursivePeerRefreshState(
          bondingAgent,
          neighborFinder,
          timerUtil,
          localPeer,
          new PeerTable(createId(999), 16),
          peerPermissions,
          5,
          100);

  @Before
  public void setup() {
    // Default peerPermissions to be permissive
    when(peerPermissions.isAllowedInPeerTable(any())).thenReturn(true);
    when(peerPermissions.allowInboundBonding(any())).thenReturn(true);
    when(peerPermissions.allowOutboundBonding(any())).thenReturn(true);
    when(peerPermissions.allowInboundNeighborsRequest(any())).thenReturn(true);
    when(peerPermissions.allowOutboundNeighborsRequest(any())).thenReturn(true);
  }

  @Test
  public void shouldBondWithInitialNodesWhenStarted() {
    recursivePeerRefreshState.start(asList(peer1, peer2, peer3), TARGET);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer3);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldOnlyBondWithUnbondedInitialNodes() {
    peer1.setStatus(PeerDiscoveryStatus.BONDED);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent, never()).performBonding(peer1);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldSkipStraightToFindNeighboursIfAllInitialNodesAreBonded() {
    peer1.setStatus(PeerDiscoveryStatus.BONDED);
    peer2.setStatus(PeerDiscoveryStatus.BONDED);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldBondWithNewlyDiscoveredNodes() {
    peer1.setStatus(PeerDiscoveryStatus.BONDED);

    recursivePeerRefreshState.start(singletonList(peer1), TARGET);

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    recursivePeerRefreshState.onNeighboursReceived(peer1, asList(peer2, peer3));

    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer3);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldMoveToNeighboursRoundWhenBondingTimesOut() {
    peer1.setStatus(PeerDiscoveryStatus.BONDED);
    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer2);
    timerUtil.runTimerHandlers();
    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldMoveToNeighboursRoundWhenBondingTimesOutVariant() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);

    completeBonding(peer1);

    timerUtil.runTimerHandlers();

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder, never()).findNeighbours(peer2, TARGET);
  }

  @Test
  public void shouldStopWhenAllNodesHaveBeenQueried() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);

    completeBonding(peer1);
    completeBonding(peer2);

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer1, emptyList());
    recursivePeerRefreshState.onNeighboursReceived(peer2, emptyList());

    verify(bondingAgent, times(2)).performBonding(any());
    verifyNoMoreInteractions(neighborFinder);
  }

  @Test
  public void shouldStopWhenMaximumNumberOfRoundsReached() {
    recursivePeerRefreshState =
        new RecursivePeerRefreshState(
            bondingAgent,
            neighborFinder,
            timerUtil,
            localPeer,
            new PeerTable(createId(999), 16),
            peerPermissions,
            5,
            1);

    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer2);

    completeBonding(peer1);
    completeBonding(peer2);

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer1, singletonList(peer3));
    recursivePeerRefreshState.onNeighboursReceived(peer2, singletonList(peer4));

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);
    verify(neighborFinder, never()).findNeighbours(peer3, TARGET);
    verify(neighborFinder, never()).findNeighbours(peer4, TARGET);
    verifyNoMoreInteractions(neighborFinder);
  }

  @Test
  public void shouldOnlyQueryClosestThreeNeighbours() {
    final Bytes id0 =
        Bytes.fromHexString(
            "0x11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
    final DiscoveryPeer peer0 = createPeer(id0, "0.0.0.0", 1, 1);
    final Bytes id1 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000");
    final DiscoveryPeer peer1 = createPeer(id1, "0.0.0.0", 1, 1);
    final Bytes id2 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010");
    final DiscoveryPeer peer2 = createPeer(id2, "0.0.0.0", 1, 1);
    final Bytes id3 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100");
    final DiscoveryPeer peer3 = createPeer(id3, "0.0.0.0", 1, 1);
    final Bytes id4 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000");
    final DiscoveryPeer peer4 = createPeer(id4, "0.0.0.0", 1, 1);

    recursivePeerRefreshState.start(singletonList(peer0), TARGET);

    // Initial bonding round
    verify(bondingAgent).performBonding(peer0);
    completeBonding(peer0);

    // Initial neighbours round
    verify(neighborFinder).findNeighbours(peer0, TARGET);
    recursivePeerRefreshState.onNeighboursReceived(peer0, asList(peer1, peer2, peer3, peer4));

    // Bonding round 2
    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer3);
    verify(bondingAgent).performBonding(peer4);

    completeBonding(peer1);
    completeBonding(peer2);
    completeBonding(peer3);
    completeBonding(peer4);

    verify(neighborFinder, never()).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);
    verify(neighborFinder).findNeighbours(peer3, TARGET);
    verify(neighborFinder).findNeighbours(peer4, TARGET);
  }

  @Test
  public void shouldNotQueryNodeThatIsAlreadyQueried() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer2);

    completeBonding(peer1);
    completeBonding(peer2);

    verify(neighborFinder, times(1)).findNeighbours(peer1, TARGET);
    verify(neighborFinder, times(1)).findNeighbours(peer2, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer1, singletonList(peer2));
    recursivePeerRefreshState.onNeighboursReceived(peer2, emptyList());

    verify(bondingAgent, times(1)).performBonding(peer1);
    verify(bondingAgent, times(1)).performBonding(peer2);
    verify(neighborFinder, times(1)).findNeighbours(peer1, TARGET);
    verify(neighborFinder, times(1)).findNeighbours(peer2, TARGET);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldNotQueryNodeMissingPermissions() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    when(peerPermissions.allowOutboundNeighborsRequest(peer1)).thenReturn(false);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer2);

    completeBonding(peer1);
    completeBonding(peer2);

    verify(neighborFinder, times(0)).findNeighbours(peer1, TARGET);
    verify(neighborFinder, times(1)).findNeighbours(peer2, TARGET);
  }

  @Test
  public void shouldBondWithNewNeighboursWhenSomeRequestsTimeOut() {
    final Bytes id0 =
        Bytes.fromHexString(
            "0x11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
    final DiscoveryPeer peer0 = createPeer(id0, "0.0.0.0", 1, 1);
    final Bytes id1 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000");
    final DiscoveryPeer peer1 = createPeer(id1, "0.0.0.0", 1, 1);
    final Bytes id2 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010");
    final DiscoveryPeer peer2 = createPeer(id2, "0.0.0.0", 1, 1);
    final Bytes id3 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100");
    final DiscoveryPeer peer3 = createPeer(id3, "0.0.0.0", 1, 1);
    final Bytes id4 =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000");
    final DiscoveryPeer peer4 = createPeer(id4, "0.0.0.0", 1, 1);
    final List<DiscoveryPeer> peerTable = asList(peer1, peer2, peer3, peer4);

    recursivePeerRefreshState.start(singletonList(peer0), TARGET);

    verify(bondingAgent).performBonding(peer0);

    completeBonding(peer0);

    verify(neighborFinder).findNeighbours(peer0, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer0, peerTable);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);
    verify(bondingAgent).performBonding(peer3);
    verify(bondingAgent).performBonding(peer4);

    completeBonding(peer1);
    completeBonding(peer2);
    completeBonding(peer3);
    completeBonding(peer4);

    verify(neighborFinder, never()).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);
    verify(neighborFinder).findNeighbours(peer3, TARGET);
    verify(neighborFinder).findNeighbours(peer4, TARGET);

    timerUtil.runTimerHandlers();

    verify(neighborFinder).findNeighbours(peer1, TARGET);
  }

  @Test
  public void shouldNotBondWithDiscoveredNodesThatAreAlreadyBonded() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);

    verify(bondingAgent, times(1)).performBonding(peer1);
    verify(bondingAgent, times(1)).performBonding(peer2);

    completeBonding(peer1);
    completeBonding(peer2);

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer1, singletonList(peer2));
    recursivePeerRefreshState.onNeighboursReceived(peer2, emptyList());

    verify(bondingAgent, times(1)).performBonding(peer2);
  }

  @Test
  public void shouldQueryNodeThatTimedOutWithBondingButLaterCompletedBonding() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);

    verify(bondingAgent, times(1)).performBonding(peer1);
    verify(bondingAgent, times(1)).performBonding(peer2);

    completeBonding(peer1);
    peer2.setStatus(PeerDiscoveryStatus.BONDING);

    timerUtil.runTimerHandlers();

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder, never()).findNeighbours(peer2, TARGET);

    // Already timed out but finally completes. DOES NOT trigger a new neighbour round.
    completeBonding(peer2);
    verify(neighborFinder, never()).findNeighbours(peer2, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer1, singletonList(peer3));

    verify(bondingAgent).performBonding(peer3);
    verify(bondingAgent, times(1)).performBonding(peer2);

    completeBonding(peer3);

    verify(neighborFinder).findNeighbours(peer2, TARGET);
    verify(neighborFinder).findNeighbours(peer3, TARGET);
  }

  @Test
  public void shouldBondWithPeersInNeighboursResponseReceivedAfterTimeout() {
    peer1.setStatus(PeerDiscoveryStatus.KNOWN);
    peer2.setStatus(PeerDiscoveryStatus.KNOWN);

    recursivePeerRefreshState.start(asList(peer1, peer2), TARGET);

    verify(bondingAgent).performBonding(peer1);
    verify(bondingAgent).performBonding(peer2);

    completeBonding(peer1);
    completeBonding(peer2);

    verify(neighborFinder).findNeighbours(peer1, TARGET);
    verify(neighborFinder).findNeighbours(peer2, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peer1, singletonList(peer3));

    timerUtil.runTimerHandlers();

    verify(bondingAgent).performBonding(peer3);
    completeBonding(peer3);

    verify(neighborFinder).findNeighbours(peer3, TARGET);

    // Receive late response from peer 2. May as well process it in this round.
    recursivePeerRefreshState.onNeighboursReceived(peer2, singletonList(peer4));

    recursivePeerRefreshState.onNeighboursReceived(peer3, emptyList());

    verify(bondingAgent).performBonding(peer4);
    verifyNoMoreInteractions(bondingAgent, neighborFinder);
  }

  @Test
  public void shouldNotBondWithNonPermittedNode() {
    final DiscoveryPeer peerA = createPeer(1, "127.0.0.1", 1, 1);
    final DiscoveryPeer peerB = createPeer(2, "127.0.0.2", 2, 2);

    when(peerPermissions.allowOutboundBonding(peerB)).thenReturn(false);

    recursivePeerRefreshState =
        new RecursivePeerRefreshState(
            bondingAgent,
            neighborFinder,
            timerUtil,
            localPeer,
            new PeerTable(createId(999), 16),
            peerPermissions,
            5,
            100);
    recursivePeerRefreshState.start(singletonList(peerA), TARGET);

    verify(bondingAgent).performBonding(peerA);

    completeBonding(peerA);

    verify(neighborFinder).findNeighbours(peerA, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peerA, Collections.singletonList(peerB));

    verify(bondingAgent, never()).performBonding(peerB);
  }

  @Test
  public void shouldNotBondWithSelf() {
    final DiscoveryPeer peerA = createPeer(1, "127.0.0.1", 1, 1);
    final DiscoveryPeer peerB = createPeer(2, "127.0.0.2", 2, 2);

    recursivePeerRefreshState.start(singletonList(peerA), TARGET);

    verify(bondingAgent).performBonding(peerA);

    completeBonding(peerA);

    verify(neighborFinder).findNeighbours(peerA, TARGET);

    recursivePeerRefreshState.onNeighboursReceived(peerA, asList(peerB, localPeer));

    verify(bondingAgent).performBonding(peerB);
    verify(bondingAgent, never()).performBonding(localPeer);
  }

  private static Bytes createId(final int id) {
    return Bytes.fromHexString(String.format("%0128x", id));
  }

  private static DiscoveryPeer createPeer(
      final int id, final String ip, final int discoveryPort, final int listeningPort) {
    return createPeer(createId(id), ip, discoveryPort, listeningPort);
  }

  private static DiscoveryPeer createPeer(
      final Bytes id, final String ip, final int discoveryPort, final int listeningPort) {
    return DiscoveryPeer.fromEnode(
        EnodeURLImpl.builder()
            .nodeId(id)
            .ipAddress(ip)
            .discoveryPort(discoveryPort)
            .listeningPort(listeningPort)
            .build());
  }

  private void completeBonding(final DiscoveryPeer peer1) {
    peer1.setStatus(PeerDiscoveryStatus.BONDED);
    recursivePeerRefreshState.onBondingComplete(peer1);
  }
}
