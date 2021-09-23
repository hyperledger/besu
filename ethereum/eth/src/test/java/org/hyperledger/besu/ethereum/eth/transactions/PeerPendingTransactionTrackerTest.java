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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PeerPendingTransactionTrackerTest {

  @Parameterized.Parameter public AbstractPendingTransactionsSorter pendingTransactions;

  private final EthPeer ethPeer1 = mock(EthPeer.class);
  private final EthPeer ethPeer2 = mock(EthPeer.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private PeerPendingTransactionTracker tracker;
  private final Hash hash1 = generator.transaction().getHash();
  private final Hash hash2 = generator.transaction().getHash();
  private final Hash hash3 = generator.transaction().getHash();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {mock(GasPricePendingTransactionsSorter.class)},
          {mock(BaseFeePendingTransactionsSorter.class)}
        });
  }

  @Before
  public void setUp() {
    tracker = new PeerPendingTransactionTracker(pendingTransactions);
    Transaction tx = mock(Transaction.class);
    when(pendingTransactions.getTransactionByHash(any())).thenReturn(Optional.of(tx));
  }

  @Test
  public void shouldTrackTransactionsToSendToPeer() {
    tracker.addToPeerSendQueue(ethPeer1, hash1);
    tracker.addToPeerSendQueue(ethPeer1, hash2);
    tracker.addToPeerSendQueue(ethPeer2, hash3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(hash1, hash2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(hash3);
  }

  @Test
  public void shouldExcludeAlreadySeenTransactionsFromTransactionsToSend() {
    tracker.markTransactionsHashesAsSeen(ethPeer1, ImmutableSet.of(hash2));

    tracker.addToPeerSendQueue(ethPeer1, hash1);
    tracker.addToPeerSendQueue(ethPeer1, hash2);
    tracker.addToPeerSendQueue(ethPeer2, hash3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(hash1);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(hash3);
  }

  @Test
  public void shouldExcludeAlreadySeenTransactionsAsACollectionFromTransactionsToSend() {
    tracker.markTransactionsHashesAsSeen(ethPeer1, ImmutableSet.of(hash1, hash2));

    tracker.addToPeerSendQueue(ethPeer1, hash1);
    tracker.addToPeerSendQueue(ethPeer1, hash2);
    tracker.addToPeerSendQueue(ethPeer2, hash3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).isEmpty();
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(hash3);
  }

  @Test
  public void shouldClearDataWhenPeerDisconnects() {
    tracker.markTransactionsHashesAsSeen(ethPeer1, ImmutableSet.of(hash3));

    tracker.addToPeerSendQueue(ethPeer1, hash2);
    tracker.addToPeerSendQueue(ethPeer2, hash3);

    tracker.onDisconnect(ethPeer1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);

    // Should have cleared data that ethPeer1 has already seen transaction1
    tracker.addToPeerSendQueue(ethPeer1, hash1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(hash1);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(hash3);
  }
}
