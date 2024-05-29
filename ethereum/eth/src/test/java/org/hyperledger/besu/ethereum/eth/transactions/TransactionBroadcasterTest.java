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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.datatypes.TransactionType.BLOB;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.toTransactionList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransactionBroadcasterTest {
  private static final Long FIXED_RANDOM_SEED = 0L;
  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private EthScheduler ethScheduler;
  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private TransactionsMessageSender transactionsMessageSender;
  @Mock private NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;

  private final EthPeer ethPeerNoEth65 = mock(EthPeer.class);
  private final EthPeer ethPeerWithEth65 = mock(EthPeer.class);
  private final EthPeer ethPeerNoEth65_2 = mock(EthPeer.class);
  private final EthPeer ethPeerWithEth65_2 = mock(EthPeer.class);
  private final EthPeer ethPeerWithEth65_3 = mock(EthPeer.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();

  private TransactionBroadcaster txBroadcaster;
  private ArgumentCaptor<Runnable> sendTaskCapture;

  @BeforeEach
  public void setUp() {
    when(ethPeerNoEth65.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.FALSE);
    when(ethPeerNoEth65_2.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.FALSE);
    when(ethPeerWithEth65.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.TRUE);
    when(ethPeerWithEth65_2.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.TRUE);
    when(ethPeerWithEth65_3.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES))
        .thenReturn(Boolean.TRUE);

    sendTaskCapture = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(ethScheduler).scheduleSyncWorkerTask(sendTaskCapture.capture());

    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);

    // we use the fixed random seed to have a predictable shuffle of peers
    txBroadcaster =
        new TransactionBroadcaster(
            ethContext,
            transactionTracker,
            transactionsMessageSender,
            newPooledTransactionHashesMessageSender,
            FIXED_RANDOM_SEED);
  }

  @Test
  public void doNotRelayTransactionsWhenPoolIsEmpty() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(0, 0);

    txBroadcaster.relayTransactionPoolTo(ethPeerNoEth65, pendingTxs);
    txBroadcaster.relayTransactionPoolTo(ethPeerWithEth65, pendingTxs);

    verifyNothingSent();
  }

  @Test
  public void relayFullTransactionsFromPoolWhenPeerDoesNotSupportEth65() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(1, 1);
    List<Transaction> txs = toTransactionList(pendingTxs);

    txBroadcaster.relayTransactionPoolTo(ethPeerNoEth65, pendingTxs);

    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth65, txs);

    sendTaskCapture.getValue().run();

    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerNoEth65);
    verifyNoInteractions(newPooledTransactionHashesMessageSender);
  }

  @Test
  public void relayTransactionHashesFromPoolWhenPeerSupportEth65() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(1, 1);
    List<Transaction> txs = toTransactionList(pendingTxs);

    txBroadcaster.relayTransactionPoolTo(ethPeerWithEth65, pendingTxs);

    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65, txs);

    sendTaskCapture.getValue().run();

    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(ethPeerWithEth65);
    verifyNoInteractions(transactionsMessageSender);
  }

  @Test
  public void onTransactionsAddedWithNoPeersDoesNothing() {
    when(ethPeers.peerCount()).thenReturn(0);

    txBroadcaster.onTransactionsAdded(toTransactionList(setupTransactionPool(1, 1)));

    verifyNothingSent();
  }

  @Test
  public void onTransactionsAddedWithOnlyNonEth65PeersSendFullTransactions() {
    when(ethPeers.peerCount()).thenReturn(2);
    when(ethPeers.streamAvailablePeers()).thenReturn(Stream.of(ethPeerNoEth65, ethPeerNoEth65_2));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);

    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth65, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth65_2, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerNoEth65);
    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerNoEth65_2);
    verifyNoInteractions(newPooledTransactionHashesMessageSender);
  }

  @Test
  public void onTransactionsAddedWithOnlyFewEth65PeersSendFullTransactions() {
    when(ethPeers.peerCount()).thenReturn(2);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.of(ethPeerWithEth65, ethPeerWithEth65_2));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);
    // the shuffled hash only peer list is always:
    // [ethPeerWithEth65_3, ethPeerWithEth65_2, ethPeerWithEth65]
    // so ethPeerWithEth65 and ethPeerWithEth65_2 are moved to the mixed broadcast list
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth65_2, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender).sendTransactionsToPeer(ethPeerWithEth65_2);
    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(ethPeerWithEth65);
  }

  @Test
  public void onTransactionsAddedWithOnlyEth65PeersSendFullTransactionsAndTransactionHashes() {
    when(ethPeers.peerCount()).thenReturn(3);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.of(ethPeerWithEth65, ethPeerWithEth65_2, ethPeerWithEth65_3));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);
    // the shuffled hash only peer list is always:
    // [ethPeerWithEth65_3, ethPeerWithEth65_2, ethPeerWithEth65]
    // so ethPeerWithEth65 and ethPeerWithEth65_2 are moved to the mixed broadcast list
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth65, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth65_2, txs);
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65_3, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender, times(2)).sendTransactionsToPeer(any(EthPeer.class));
    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(any(EthPeer.class));
  }

  @Test
  public void onTransactionsAddedWithMixedPeersSendFullTransactionsAndTransactionHashes() {
    List<EthPeer> eth65Peers = List.of(ethPeerWithEth65, ethPeerWithEth65_2);

    when(ethPeers.peerCount()).thenReturn(3);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.concat(eth65Peers.stream(), Stream.of(ethPeerNoEth65)));

    List<Transaction> txs = toTransactionList(setupTransactionPool(1, 1));

    txBroadcaster.onTransactionsAdded(txs);
    // the shuffled hash only peer list is always:
    // [ethPeerWithEth65, ethPeerWithEth65_2]
    // so ethPeerWithEth65_2 is moved to the mixed broadcast list
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth65_2, txs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth65, txs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    ArgumentCaptor<EthPeer> capPeerFullTransactions = ArgumentCaptor.forClass(EthPeer.class);
    verify(transactionsMessageSender, times(2))
        .sendTransactionsToPeer(capPeerFullTransactions.capture());
    List<EthPeer> fullTransactionPeers = new ArrayList<>(capPeerFullTransactions.getAllValues());
    assertThat(fullTransactionPeers.remove(ethPeerNoEth65)).isTrue();
    assertThat(fullTransactionPeers).hasSize(1).first().isIn(eth65Peers);

    ArgumentCaptor<EthPeer> capPeerTransactionHashes = ArgumentCaptor.forClass(EthPeer.class);
    verify(newPooledTransactionHashesMessageSender)
        .sendTransactionHashesToPeer(capPeerTransactionHashes.capture());
    assertThat(capPeerTransactionHashes.getValue()).isIn(eth65Peers);
  }

  @Test
  public void
      onTransactionsAddedWithMixedPeersAndHashOnlyBroadcastTransactionsSendTransactionHashes() {
    List<EthPeer> eth65Peers = List.of(ethPeerWithEth65, ethPeerWithEth65_2);

    when(ethPeers.peerCount()).thenReturn(3);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.concat(eth65Peers.stream(), Stream.of(ethPeerNoEth65)));

    List<Transaction> txs = toTransactionList(setupTransactionPool(BLOB, 0, 1));

    txBroadcaster.onTransactionsAdded(txs);
    // the shuffled hash only peer list is always:
    // [ethPeerWithEth65, ethPeerWithEth65_2]
    // so ethPeerWithEth65_2 is moved to the mixed broadcast list
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65, txs);
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65_2, txs);
    verifyNoTransactionAddedToPeerSendingQueue(ethPeerNoEth65);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender, times(0)).sendTransactionsToPeer(any());

    ArgumentCaptor<EthPeer> capPeerOnlyHashes = ArgumentCaptor.forClass(EthPeer.class);
    verify(newPooledTransactionHashesMessageSender, times(2))
        .sendTransactionHashesToPeer(capPeerOnlyHashes.capture());
    List<EthPeer> onlyHashPeers = new ArrayList<>(capPeerOnlyHashes.getAllValues());
    assertThat(onlyHashPeers).hasSameElementsAs(eth65Peers);
  }

  @Test
  public void onTransactionsAddedWithMixedPeersAndMixedBroadcastKind() {
    List<EthPeer> eth65Peers = List.of(ethPeerWithEth65, ethPeerWithEth65_2);

    when(ethPeers.peerCount()).thenReturn(3);
    when(ethPeers.streamAvailablePeers())
        .thenReturn(Stream.concat(eth65Peers.stream(), Stream.of(ethPeerNoEth65)));

    // 1 full broadcast transaction type
    // 1 hash only broadcast transaction type
    List<Transaction> fullBroadcastTxs =
        toTransactionList(setupTransactionPool(TransactionType.EIP1559, 0, 1));
    List<Transaction> hashBroadcastTxs = toTransactionList(setupTransactionPool(BLOB, 0, 1));

    List<Transaction> mixedTxs = new ArrayList<>(fullBroadcastTxs);
    mixedTxs.addAll(hashBroadcastTxs);

    txBroadcaster.onTransactionsAdded(mixedTxs);
    // the shuffled hash only peer list is always:
    // [ethPeerWithEth65, ethPeerWithEth65_2]
    // so ethPeerWithEth65_2 is moved to the mixed broadcast list
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65, mixedTxs);
    verifyTransactionAddedToPeerHashSendingQueue(ethPeerWithEth65_2, hashBroadcastTxs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerWithEth65_2, fullBroadcastTxs);
    verifyTransactionAddedToPeerSendingQueue(ethPeerNoEth65, fullBroadcastTxs);

    sendTaskCapture.getAllValues().forEach(Runnable::run);

    verify(transactionsMessageSender, times(1)).sendTransactionsToPeer(ethPeerNoEth65);

    ArgumentCaptor<EthPeer> capPeerOnlyHashes = ArgumentCaptor.forClass(EthPeer.class);
    verify(newPooledTransactionHashesMessageSender, times(2))
        .sendTransactionHashesToPeer(capPeerOnlyHashes.capture());
    List<EthPeer> onlyHashPeers = new ArrayList<>(capPeerOnlyHashes.getAllValues());
    assertThat(onlyHashPeers).hasSameElementsAs(eth65Peers);
  }

  private void verifyNothingSent() {
    verifyNoInteractions(
        transactionTracker, transactionsMessageSender, newPooledTransactionHashesMessageSender);
  }

  private Set<PendingTransaction> setupTransactionPool(
      final int numLocalTransactions, final int numRemoteTransactions) {
    Set<PendingTransaction> pendingTxs = createPendingTransactionList(numLocalTransactions, true);
    pendingTxs.addAll(createPendingTransactionList(numRemoteTransactions, false));

    return pendingTxs;
  }

  private Set<PendingTransaction> setupTransactionPool(
      final TransactionType type, final int numLocalTransactions, final int numRemoteTransactions) {
    Set<PendingTransaction> pendingTxs =
        createPendingTransactionList(type, numLocalTransactions, true);
    pendingTxs.addAll(createPendingTransactionList(type, numRemoteTransactions, false));

    return pendingTxs;
  }

  private Set<PendingTransaction> createPendingTransactionList(final int num, final boolean local) {
    return IntStream.range(0, num)
        .mapToObj(unused -> generator.transaction())
        .map(tx -> local ? new PendingTransaction.Local(tx) : new PendingTransaction.Remote(tx))
        .collect(Collectors.toSet());
  }

  private Set<PendingTransaction> createPendingTransactionList(
      final TransactionType type, final int num, final boolean local) {
    return IntStream.range(0, num)
        .mapToObj(unused -> generator.transaction(type))
        .map(tx -> local ? new PendingTransaction.Local(tx) : new PendingTransaction.Remote(tx))
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

  private void verifyTransactionAddedToPeerHashSendingQueue(
      final EthPeer peer, final Collection<Transaction> transactions) {

    ArgumentCaptor<Transaction> trackedTransactions = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionTracker, times(transactions.size()))
        .addToPeerHashSendQueue(eq(peer), trackedTransactions.capture());
    assertThat(trackedTransactions.getAllValues())
        .containsExactlyInAnyOrderElementsOf(transactions);
  }

  private void verifyNoTransactionAddedToPeerSendingQueue(final EthPeer peer) {

    verify(transactionTracker, times(0)).addToPeerSendQueue(eq(peer), any());
  }
}
