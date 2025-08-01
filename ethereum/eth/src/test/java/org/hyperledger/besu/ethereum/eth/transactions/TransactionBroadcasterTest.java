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
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction.toTransactionList;
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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
  @Mock private EthContext ethContext;
  @Mock private EthPeers ethPeers;
  @Mock private EthScheduler ethScheduler;
  @Mock private PeerTransactionTracker transactionTracker;
  @Mock private NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;
  private final EthPeer ethPeer = mock(EthPeer.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();

  private TransactionBroadcaster txBroadcaster;
  private ArgumentCaptor<Runnable> sendTaskCapture;

  @BeforeEach
  public void setUp() {
    sendTaskCapture = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(ethScheduler).scheduleSyncWorkerTask(sendTaskCapture.capture());

    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);

    // we use the fixed random seed to have a predictable shuffle of peers
    txBroadcaster =
        new TransactionBroadcaster(
            ethContext, transactionTracker, newPooledTransactionHashesMessageSender);
  }

  @Test
  public void doNotRelayTransactionsWhenPoolIsEmpty() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(0, 0);
    txBroadcaster.relayTransactionPoolTo(ethPeer, pendingTxs);

    verifyNothingSent();
  }

  @Test
  public void relayTransactionHashesFromPoolWhenPeerSupportEth65() {
    Collection<PendingTransaction> pendingTxs = setupTransactionPool(1, 1);
    List<Transaction> txs = toTransactionList(pendingTxs);

    txBroadcaster.relayTransactionPoolTo(ethPeer, pendingTxs);

    verifyTransactionAddedToPeerHashSendingQueue(ethPeer, txs);

    sendTaskCapture.getValue().run();

    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(ethPeer);
  }

  @Test
  public void onTransactionsAddedWithNoPeersDoesNothing() {
    when(ethPeers.peerCount()).thenReturn(0);

    txBroadcaster.onTransactionsAdded(toTransactionList(setupTransactionPool(1, 1)));

    verifyNothingSent();
  }

  private void verifyNothingSent() {
    verifyNoInteractions(transactionTracker, newPooledTransactionHashesMessageSender);
  }

  private Set<PendingTransaction> setupTransactionPool(
      final int numLocalTransactions, final int numRemoteTransactions) {
    Set<PendingTransaction> pendingTxs = createPendingTransactionList(numLocalTransactions, true);
    pendingTxs.addAll(createPendingTransactionList(numRemoteTransactions, false));

    return pendingTxs;
  }

  private Set<PendingTransaction> createPendingTransactionList(final int num, final boolean local) {
    return IntStream.range(0, num)
        .mapToObj(unused -> generator.transaction())
        .map(tx -> local ? new PendingTransaction.Local(tx) : new PendingTransaction.Remote(tx))
        .collect(Collectors.toSet());
  }

  private void verifyTransactionAddedToPeerHashSendingQueue(
      final EthPeer peer, final Collection<Transaction> transactions) {

    ArgumentCaptor<Transaction> trackedTransactions = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionTracker, times(transactions.size()))
        .addToPeerHashSendQueue(eq(peer), trackedTransactions.capture());
    assertThat(trackedTransactions.getAllValues())
        .containsExactlyInAnyOrderElementsOf(transactions);
  }
}
