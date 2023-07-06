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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.TransactionsMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TransactionsMessageSenderTest {

  private final EthPeer peer1 = mock(EthPeer.class);
  private final EthPeer peer2 = mock(EthPeer.class);

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  private final PeerTransactionTracker transactionTracker = new PeerTransactionTracker();
  private final TransactionsMessageSender messageSender =
      new TransactionsMessageSender(transactionTracker);

  @Test
  public void shouldSendTransactionsToEachPeer() throws Exception {
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
    final Set<Transaction> transactions = generator.transactions(6000);

    transactions.forEach(transaction -> transactionTracker.addToPeerSendQueue(peer1, transaction));

    messageSender.sendTransactionsToPeers();
    final ArgumentCaptor<MessageData> messageDataArgumentCaptor =
        ArgumentCaptor.forClass(MessageData.class);
    verify(peer1, times(2)).send(messageDataArgumentCaptor.capture());

    final List<MessageData> sentMessages = messageDataArgumentCaptor.getAllValues();

    assertThat(sentMessages).hasSize(2);
    assertThat(sentMessages).allMatch(message -> message.getCode() == EthPV62.TRANSACTIONS);
    final Set<Transaction> firstBatch = getTransactionsFromMessage(sentMessages.get(0));
    final Set<Transaction> secondBatch = getTransactionsFromMessage(sentMessages.get(1));

    assertThat(firstBatch).hasSizeGreaterThan(secondBatch.size());
    assertThat(Sets.union(firstBatch, secondBatch)).isEqualTo(transactions);
  }

  private MessageData transactionsMessageContaining(final Transaction... transactions) {
    return argThat(
        message -> {
          final Set<Transaction> actualSentTransactions = getTransactionsFromMessage(message);
          final Set<Transaction> expectedTransactions = newHashSet(transactions);
          return message.getCode() == EthPV62.TRANSACTIONS
              && actualSentTransactions.equals(expectedTransactions);
        });
  }

  private Set<Transaction> getTransactionsFromMessage(final MessageData message) {
    final TransactionsMessage transactionsMessage = TransactionsMessage.readFrom(message);
    return newHashSet(transactionsMessage.transactions());
  }
}
