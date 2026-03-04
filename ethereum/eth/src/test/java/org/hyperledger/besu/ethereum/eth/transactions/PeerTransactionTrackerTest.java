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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.PeerReputation;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

public class PeerTransactionTrackerTest {
  private final EthPeers ethPeers = mock(EthPeers.class);
  private final EthScheduler ethScheduler = new DeterministicEthScheduler();
  private final EthPeer ethPeer1 = mockPeer();
  private final EthPeer ethPeer2 = mockPeer();
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();
  private final PeerTransactionTracker tracker =
      new PeerTransactionTracker(TransactionPoolConfiguration.DEFAULT, ethPeers, ethScheduler);
  private final PeerTransactionTracker forgetfulTracker =
      new PeerTransactionTracker(
          ImmutableTransactionPoolConfiguration.builder()
              .unstable(
                  ImmutableTransactionPoolConfiguration.Unstable.builder()
                      .peerTrackerForgetEvictedTxs(true)
                      .build())
              .build(),
          ethPeers,
          ethScheduler);
  private final PeerTransactionTracker shortMemoryTracker =
      new PeerTransactionTracker(
          ImmutableTransactionPoolConfiguration.builder()
              .unstable(
                  ImmutableTransactionPoolConfiguration.Unstable.builder()
                      .maxTrackedSeenTxsPerPeer(2)
                      .build())
              .build(),
          ethPeers,
          ethScheduler);

  @Test
  public void shouldTrackTransactionsToSendToPeer() {
    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction1));
    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction2));
    tracker.addToPeerSendQueue(ethPeer2, List.of(transaction3));

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(claimAllTransactionsToSend(tracker, ethPeer1))
        .containsOnly(transaction1, transaction2);
    assertThat(claimAllTransactionsToSend(tracker, ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldTrackSeenTransactionStatePerPeer() {
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction2.getHash()));

    // tx2 marked as seen only for peer1
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction2)).isTrue();
    // not for peer2
    assertThat(tracker.hasPeerSeenTransaction(ethPeer2, transaction2)).isFalse();
    // tx1 not seen for either peer
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction1)).isFalse();
  }

  @Test
  public void shouldStopTrackingSeenTransactionsWhenRemovalReasonSaysSo() {
    forgetfulTracker.markTransactionsAsSeen(ethPeer1, List.of(transaction2.getHash()));

    assertThat(forgetfulTracker.hasSeenTransaction(transaction2.getHash())).isTrue();

    forgetfulTracker.onTransactionDropped(transaction2, createRemovalReason(true, false));

    assertThat(forgetfulTracker.hasSeenTransaction(transaction2.getHash())).isFalse();
  }

  @Test
  public void shouldKeepTrackingSeenTransactionsWhenNotForgettingEvenIfRemovalReasonSaysSo() {
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction2.getHash()));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();

    tracker.onTransactionDropped(transaction2, createRemovalReason(true, false));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();
  }

  @Test
  public void shouldRemoveTheLastRecentSeenTransactionWhenTheCacheIsFull() {
    shortMemoryTracker.markTransactionsAsSeen(
        ethPeer1, List.of(transaction1.getHash(), transaction2.getHash()));

    assertThat(shortMemoryTracker.hasSeenTransaction(transaction1.getHash())).isTrue();
    assertThat(shortMemoryTracker.hasSeenTransaction(transaction2.getHash())).isTrue();

    // now the cache is full and the last recent entry if the transaction1
    // so it should be evicted when inserting transaction3
    shortMemoryTracker.markTransactionsAsSeen(ethPeer1, List.of(transaction3.getHash()));

    assertThat(shortMemoryTracker.hasSeenTransaction(transaction1.getHash())).isFalse();
    assertThat(shortMemoryTracker.hasSeenTransaction(transaction2.getHash())).isTrue();
    assertThat(shortMemoryTracker.hasSeenTransaction(transaction3.getHash())).isTrue();
  }

  @Test
  public void shouldKeepTrackingSeenTransactionsWhenRemovalReasonSaysSo() {
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction2.getHash()));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();

    tracker.onTransactionDropped(transaction2, createRemovalReason(false, false));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();
  }

  @Test
  public void shouldTrackSeenTransactionStateForCollectionPerPeer() {
    tracker.markTransactionsAsSeen(
        ethPeer1, List.of(transaction1.getHash(), transaction2.getHash()));

    // both tx1 and tx2 marked as seen for peer1
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction1)).isTrue();
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction2)).isTrue();
    // not for peer2
    assertThat(tracker.hasPeerSeenTransaction(ethPeer2, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(ethPeer2, transaction2)).isFalse();
  }

  @Test
  public void shouldClearDataWhenPeerDisconnects() {
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction1.getHash()));

    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction2));
    tracker.addToPeerSendQueue(ethPeer2, List.of(transaction3));

    when(ethPeers.streamAllPeers())
        .thenReturn(Stream.of(ethPeer2).map(EthPeerImmutableAttributes::from));
    tracker.onDisconnect(ethPeer1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);

    // Should have cleared data that ethPeer1 has already seen transaction1
    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction1));

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(claimAllTransactionsToSend(tracker, ethPeer1)).containsOnly(transaction1);
    assertThat(claimAllTransactionsToSend(tracker, ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldClearDataForAllDisconnectedPeers() {
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction1.getHash()));
    tracker.markTransactionsAsSeen(ethPeer2, List.of(transaction2.getHash()));

    when(ethPeers.streamAllPeers())
        .thenReturn(Stream.of(ethPeer2).map(EthPeerImmutableAttributes::from));
    tracker.onDisconnect(ethPeer1);

    // false because tracker removed for ethPeer1
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(ethPeer2, transaction2)).isTrue();

    // simulate a concurrent interaction, that just after the disconnection of the peer,
    // recreates the transaction tackers for it
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction1.getHash()));
    // ethPeer1 is here again, due to the above interaction with the tracker
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction1)).isTrue();

    // disconnection of ethPeers2 will reconcile the tracker, removing also all the other
    // disconnected peers
    when(ethPeers.streamAllPeers()).thenReturn(Stream.of());
    tracker.onDisconnect(ethPeer2);

    // since no peers are connected, all the transaction trackers have been removed
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(ethPeer2, transaction2)).isFalse();
  }

  private RemovalReason createRemovalReason(
      final boolean stopTracking, final boolean stopBroadcasting) {
    return new RemovalReason() {

      @Override
      public String label() {
        return "";
      }

      @Override
      public boolean stopTracking() {
        return stopTracking;
      }

      @Override
      public boolean stopBroadcasting() {
        return stopBroadcasting;
      }
    };
  }

  @Test
  public void shouldRemoveConfirmedTransactionsFromAllQueuesOnBlockAdded() {
    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction1, transaction2));
    tracker.addToPeerAnnouncementsSendQueue(ethPeer1, List.of(transaction1, transaction3));
    tracker.receivedTransactionAnnouncements(ethPeer2, List.of(transaction1.getHash()));

    final Block block =
        generator.block(BlockDataGenerator.BlockOptions.create().addTransaction(transaction1));
    tracker.onBlockAdded(BlockAddedEvent.createForHeadAdvancement(block, List.of(), List.of()));

    // transaction1 removed from full-tx send queue
    assertThat(claimAllTransactionsToSend(tracker, ethPeer1)).containsOnly(transaction2);
    // transaction1 removed from announcements send queue
    assertThat(claimAllAnnouncementsToSend(tracker, ethPeer1)).containsOnly(transaction3);
    // transaction1 removed from peer2's announcement request queue
    assertThat(tracker.claimTransactionAnnouncementsToRequestFromPeer(ethPeer2, 10)).isEmpty();
    // transaction1 recorded as recently confirmed
    assertThat(tracker.hasSeenTransaction(transaction1.getHash())).isTrue();
    // unconfirmed transactions not affected
    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isFalse();
  }

  @Test
  public void shouldNotRetryMissedAnnouncementFromSamePeer() {
    tracker.receivedTransactionAnnouncements(ethPeer1, List.of(transaction1.getHash()));

    // Claim the announcement for an in-flight fetch
    final List<Hash> claimed = tracker.claimTransactionAnnouncementsToRequestFromPeer(ethPeer1, 10);
    assertThat(claimed).containsOnly(transaction1.getHash());

    // Peer re-announces the same tx while the fetch is in-flight
    tracker.receivedTransactionAnnouncements(ethPeer1, List.of(transaction1.getHash()));

    // Fetch completes but transaction was not returned by the peer (missed)
    tracker.missedTransactionAnnouncements(ethPeer1, claimed);
    tracker.consumedTransactionAnnouncements(claimed);

    // Should not retry from the same peer since it was missed
    assertThat(tracker.claimTransactionAnnouncementsToRequestFromPeer(ethPeer1, 10)).isEmpty();
  }

  @Test
  public void shouldNotRemoveTransactionFromSendQueuesWhenStopBroadcastingIsFalse() {
    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction1));
    tracker.addToPeerAnnouncementsSendQueue(ethPeer1, List.of(transaction1));

    // stopBroadcasting=false (e.g. RECONCILED): queues must not be cleared
    tracker.onTransactionDropped(transaction1, createRemovalReason(false, false));

    assertThat(claimAllTransactionsToSend(tracker, ethPeer1)).containsOnly(transaction1);
    assertThat(claimAllAnnouncementsToSend(tracker, ethPeer1)).containsOnly(transaction1);
  }

  @Test
  public void shouldRemoveTransactionFromSendQueuesWhenStopBroadcastingIsTrue() {
    tracker.addToPeerSendQueue(ethPeer1, List.of(transaction1));
    tracker.addToPeerAnnouncementsSendQueue(ethPeer1, List.of(transaction1));

    tracker.onTransactionDropped(transaction1, createRemovalReason(false, true));

    assertThat(claimAllTransactionsToSend(tracker, ethPeer1)).isEmpty();
    assertThat(claimAllAnnouncementsToSend(tracker, ethPeer1)).isEmpty();
  }

  private List<Transaction> claimAllAnnouncementsToSend(
      final PeerTransactionTracker tracker, final EthPeer peer) {
    final List<Transaction> result = new ArrayList<>();
    Transaction tx;
    while ((tx = tracker.claimTransactionAnnouncementToSendToPeer(peer)) != null) {
      result.add(tx);
    }
    return result;
  }

  private List<Transaction> claimAllTransactionsToSend(
      final PeerTransactionTracker tracker, final EthPeer peer) {
    final List<Transaction> result = new ArrayList<>();
    Transaction tx;
    while ((tx = tracker.claimTransactionToSendToPeer(peer)) != null) {
      result.add(tx);
    }
    return result;
  }

  private EthPeer mockPeer() {
    final EthPeer peer = mock(EthPeer.class);
    final ChainState chainState = new ChainState();
    chainState.updateHeightEstimate(0);
    chainState.statusReceived(Hash.EMPTY, Difficulty.of(0));
    when(peer.chainState()).thenReturn(chainState);
    when(peer.getReputation()).thenReturn(new PeerReputation());
    PeerConnection connection = mock(PeerConnection.class);
    when(peer.getConnection()).thenReturn(connection);
    return peer;
  }
}
