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

import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PendingTransactionsMessageSenderTest {

  private final EthPeer peer1 = mock(EthPeer.class);
  private final EthPeer peer2 = mock(EthPeer.class);

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Hash transaction1 = generator.transaction().getHash();
  private final Hash transaction2 = generator.transaction().getHash();
  private final Hash transaction3 = generator.transaction().getHash();

  private final PendingTransactions pendingTransactions = mock(PendingTransactions.class);
  private final PeerPendingTransactionTracker transactionTracker =
      new PeerPendingTransactionTracker(pendingTransactions);
  private final PendingTransactionsMessageSender messageSender =
      new PendingTransactionsMessageSender(transactionTracker);

  @Before
  public void setUp() {
    Transaction tx = mock(Transaction.class);
    when(pendingTransactions.getTransactionByHash(any())).thenReturn(Optional.of(tx));
  }

  @Test
  public void shouldSendPendingTransactionsToEachPeer() throws Exception {

    transactionTracker.addToPeerSendQueue(peer1, transaction1);
    transactionTracker.addToPeerSendQueue(peer1, transaction2);
    transactionTracker.addToPeerSendQueue(peer2, transaction3);

    messageSender.sendTransactionsToPeers();

    verify(peer1).send(transactionsMessageContaining(transaction1, transaction2));
    verify(peer2).send(transactionsMessageContaining(transaction3));
    verifyNoMoreInteractions(peer1, peer2);
  }

  @Test
  public void shouldSendTransactionsInBatchesWithLimit() throws Exception {
    final Set<Hash> transactions =
        generator.transactions(6000).stream().map(Transaction::getHash).collect(Collectors.toSet());

    transactions.forEach(transaction -> transactionTracker.addToPeerSendQueue(peer1, transaction));

    messageSender.sendTransactionsToPeers();
    final ArgumentCaptor<MessageData> messageDataArgumentCaptor =
        ArgumentCaptor.forClass(MessageData.class);
    verify(peer1, times(2)).send(messageDataArgumentCaptor.capture());

    final List<MessageData> sentMessages = messageDataArgumentCaptor.getAllValues();

    assertThat(sentMessages).hasSize(2);
    assertThat(sentMessages)
        .allMatch(message -> message.getCode() == EthPV65.NEW_POOLED_TRANSACTION_HASHES);
    final Set<Hash> firstBatch = getTransactionsFromMessage(sentMessages.get(0));
    final Set<Hash> secondBatch = getTransactionsFromMessage(sentMessages.get(1));

    final int expectedFirstBatchSize = 4096, expectedSecondBatchSize = 1904, toleranceDelta = 0;
    assertThat(firstBatch)
        .hasSizeBetween(
            expectedFirstBatchSize - toleranceDelta, expectedFirstBatchSize + toleranceDelta);
    assertThat(secondBatch)
        .hasSizeBetween(
            expectedSecondBatchSize - toleranceDelta, expectedSecondBatchSize + toleranceDelta);

    assertThat(Sets.union(firstBatch, secondBatch)).isEqualTo(transactions);
  }

  private MessageData transactionsMessageContaining(final Hash... transactions) {
    return argThat(
        message -> {
          final Set<Hash> actualSentTransactions = getTransactionsFromMessage(message);
          final Set<Hash> expectedTransactions = newHashSet(transactions);
          return message.getCode() == EthPV65.NEW_POOLED_TRANSACTION_HASHES
              && actualSentTransactions.equals(expectedTransactions);
        });
  }

  private Set<Hash> getTransactionsFromMessage(final MessageData message) {
    final NewPooledTransactionHashesMessage transactionsMessage =
        NewPooledTransactionHashesMessage.readFrom(message);
    return newHashSet(transactionsMessage.pendingTransactions());
  }
}
