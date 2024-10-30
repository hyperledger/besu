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

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;

import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

public class PeerTransactionTrackerTest {
  private final EthPeers ethPeers = mock(EthPeers.class);

  private final EthPeer ethPeer1 = mock(EthPeer.class);
  private final EthPeer ethPeer2 = mock(EthPeer.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final PeerTransactionTracker tracker = new PeerTransactionTracker(ethPeers);
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  @Test
  public void shouldTrackTransactionsToSendToPeer() {
    tracker.addToPeerSendQueue(ethPeer1, transaction1);
    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1))
        .containsOnly(transaction1, transaction2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldExcludeAlreadySeenTransactionsFromTransactionsToSend() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction2));

    tracker.addToPeerSendQueue(ethPeer1, transaction1);
    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(transaction1);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldStopTrackingSeenTransactionsWhenRemovalReasonSaysSo() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction2));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();

    tracker.onTransactionDropped(transaction2, createRemovalReason(true));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isFalse();
  }

  @Test
  public void shouldKeepTrackingSeenTransactionsWhenRemovalReasonSaysSo() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction2));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();

    tracker.onTransactionDropped(transaction2, createRemovalReason(false));

    assertThat(tracker.hasSeenTransaction(transaction2.getHash())).isTrue();
  }

  @Test
  public void shouldExcludeAlreadySeenTransactionsAsACollectionFromTransactionsToSend() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction1, transaction2));

    tracker.addToPeerSendQueue(ethPeer1, transaction1);
    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).isEmpty();
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldClearDataWhenPeerDisconnects() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction1));

    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    when(ethPeers.streamAllPeers()).thenReturn(Stream.of(ethPeer2));
    tracker.onDisconnect(ethPeer1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);

    // Should have cleared data that ethPeer1 has already seen transaction1
    tracker.addToPeerSendQueue(ethPeer1, transaction1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(transaction1);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldClearDataForAllDisconnectedPeers() {
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction1));
    tracker.markTransactionsAsSeen(ethPeer2, List.of(transaction2));

    when(ethPeers.streamAllPeers()).thenReturn(Stream.of(ethPeer2));
    tracker.onDisconnect(ethPeer1);

    // false because tracker removed for ethPeer1
    assertThat(tracker.hasPeerSeenTransaction(ethPeer1, transaction1)).isFalse();
    assertThat(tracker.hasPeerSeenTransaction(ethPeer2, transaction2)).isTrue();

    // simulate a concurrent interaction, that just after the disconnection of the peer,
    // recreates the transaction tackers for it
    tracker.markTransactionsAsSeen(ethPeer1, List.of(transaction1));
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

  private RemovalReason createRemovalReason(final boolean stopTracking) {
    return new RemovalReason() {

      @Override
      public String label() {
        return "";
      }

      @Override
      public boolean stopTracking() {
        return stopTracking;
      }
    };
  }
}
