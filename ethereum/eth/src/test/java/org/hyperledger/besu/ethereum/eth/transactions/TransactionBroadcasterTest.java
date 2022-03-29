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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo.toTransactionList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionBroadcasterTest {

  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private EthScheduler ethScheduler;
  @Mock private AbstractPendingTransactionsSorter pendingTransactions;
  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private TransactionsMessageSender transactionsMessageSender;
  @Mock private NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;

  private final EthPeer ethPeerNoEth66 = mock(EthPeer.class);
  private final EthPeer ethPeerWithEth66 = mock(EthPeer.class);
  private final EthPeer ethPeerNoEth66_2 = mock(EthPeer.class);
  private final EthPeer ethPeerWithEth66_2 = mock(EthPeer.class);
  private final EthPeer ethPeerWithEth66_3 = mock(EthPeer.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();

  private TransactionBroadcaster txBroadcaster;
  private ArgumentCaptor<Runnable> sendTaskCapture;

  @Before
  public void setUp() {
    when(ethPeerNoEth66.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.FALSE);
    when(ethPeerNoEth66_2.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.FALSE);
    when(ethPeerWithEth66.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.TRUE);
    when(ethPeerWithEth66_2.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.TRUE);
    when(ethPeerWithEth66_3.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.TRUE);

    sendTaskCapture = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(ethScheduler).scheduleSyncWorkerTask(sendTaskCapture.capture());

    when(ethPeers.getMaxPeers()).thenReturn(4);

    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);

    txBroadcaster =
        new TransactionBroadcaster(
            ethContext,
            pendingTransactions,
            transactionTracker,
            transactionsMessageSender,
            newPooledTransactionHashesMessageSender);
  }

  @Test
  public void doNotRelayTransactionsWhenPoolIsEmpty() {
    setupTransactionPool(0, 0);

    txBroadcaster.relayTransactionPoolTo(ethPeerNoEth66);
    txBroadcaster.relayTransactionPoolTo(ethPeerWithEth66);

    verifyNothingSent();
  }

  @Test
  public void relayFullTransactionsFromPoolWhenPeerDoesNotSupportEth66() {
    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.relayTransactionPoolTo(ethPeerNoEth66);

    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth66, txs);

    sendTaskCapture.getValue().run();

    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerNoEth66);
    verifyNoInteractions(newPooledTransactionHashesMessageSender);
  }

  @Test
  public void relayTransactionHashesFromPoolWhenPeerSupportEth66() {
    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.relayTransactionPoolTo(ethPeerWithEth66);

    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66, txs);

    sendTaskCapture.getValue().run();

    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(ethPeerWithEth66);
    verifyNoInteractions(transactionsMessageSender);
  }

  @Test
  public void onTransactionsAddedWithNoPeersDoesNothing() {
    when(ethPeers.peerCount()).thenReturn(0);

    txBroadcaster.onTransactionsAdded(toTransactionList(setupTransactionPool(1, 1)));

    verifyNothingSent();
  }

  @Test
  public void onTransactionsAddedWithOnlyNonEth66PeersSendFullTransactions() {
    when(ethPeers.peerCount()).thenReturn(2);
    when(ethPeers.streamAvailablePeers()).thenReturn(Stream.of(ethPeerNoEth66, ethPeerNoEth66_2));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);

    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth66, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth66_2, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerNoEth66);
    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerNoEth66_2);
    verifyNoInteractions(newPooledTransactionHashesMessageSender);
  }

  @Test
  public void onTransactionsAddedWithOnlyFewEth66PeersSendFullTransactions() {
    when(ethPeers.peerCount()).thenReturn(2);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.of(ethPeerWithEth66, ethPeerWithEth66_2));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);

    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66_2, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender, times(2)).sendTransactionsToPeer(any(EthPeer.class));
    verifyNoInteractions(newPooledTransactionHashesMessageSender);
  }

  @Test
  public void onTransactionsAddedWithOnlyEth66PeersSendFullTransactionsAndTransactionHashes() {
    when(ethPeers.peerCount()).thenReturn(3);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.of(ethPeerWithEth66, ethPeerWithEth66_2, ethPeerWithEth66_3));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);

    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66_2, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66_3, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender, times(2)).sendTransactionsToPeer(any(EthPeer.class));
    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(any(EthPeer.class));
  }

  @Test
  public void onTransactionsAddedWithMixedPeersSendFullTransactionsAndTransactionHashes() {
    List<EthPeer> eth66Peers = List.of(ethPeerWithEth66, ethPeerWithEth66_2);

    when(ethPeers.peerCount()).thenReturn(3);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.concat(eth66Peers.stream(), Stream.of(ethPeerNoEth66)));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);

    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth66_2, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth66, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    ArgumentCaptor<EthPeer> capPeerFullTransactions = ArgumentCaptor.forClass(EthPeer.class);
    verify(transactionsMessageSender, times(2))
        .sendTransactionsToPeer(capPeerFullTransactions.capture());
    List<EthPeer> fullTransactionPeers = new ArrayList<>(capPeerFullTransactions.getAllValues());
    assertThat(fullTransactionPeers.remove(ethPeerNoEth66)).isTrue();
    assertThat(fullTransactionPeers).hasSize(1).first().isIn(eth66Peers);

    ArgumentCaptor<EthPeer> capPeerTransactionHashes = ArgumentCaptor.forClass(EthPeer.class);
    verify(newPooledTransactionHashesMessageSender)
        .sendTransactionHashesToPeer(capPeerTransactionHashes.capture());
    assertThat(capPeerTransactionHashes.getValue()).isIn(eth66Peers);
  }

  private void verifyNothingSent() {
    verifyNoInteractions(
        transactionTracker, transactionsMessageSender, newPooledTransactionHashesMessageSender);
  }

  private Set<TransactionInfo> setupTransactionPool(
      final int numLocalTransactions, final int numRemoteTransactions) {
    Set<TransactionInfo> txInfo = createTransactionInfoList(numLocalTransactions, true);
    txInfo.addAll(createTransactionInfoList(numRemoteTransactions, false));

    when(pendingTransactions.getTransactionInfo()).thenReturn(txInfo);

    return txInfo;
  }

  private Set<TransactionInfo> createTransactionInfoList(final int num, final boolean local) {
    return IntStream.range(0, num)
        .mapToObj(unused -> generator.transaction())
        .map(tx -> new TransactionInfo(tx, local, Instant.now()))
        .collect(Collectors.toSet());
  }

  private void verifyTransactionAddedToPeerSendingQueue(
      final EthPeer peer, final Collection<Transaction> transactions) {

    ArgumentCaptor<Transaction> trackedTransactions = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionTracker, times(transactions.size()))
        .addToPeerSendQueue(eq(peer), trackedTransactions.capture());
    assertThat(trackedTransactions.getAllValues())
        .containsExactlyInAnyOrderElementsOf(transactions);
  }
}
