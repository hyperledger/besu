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
import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class NewPooledTransactionHashesMessageSenderTest {
  private final EthPeers ethPeers = mock(EthPeers.class);

  private final EthPeer peer1 = mock(EthPeer.class);
  private final EthPeer peer2 = mock(EthPeer.class);

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  public PendingTransactions pendingTransactions;

  private PeerTransactionTracker transactionTracker;
  private NewPooledTransactionHashesMessageSender messageSender;

  @BeforeEach
  public void setUp() {
    transactionTracker = new PeerTransactionTracker(ethPeers);
    messageSender = new NewPooledTransactionHashesMessageSender(transactionTracker);
    final Transaction tx = mock(Transaction.class);
    pendingTransactions = mock(PendingTransactions.class);
    when(pendingTransactions.getTransactionByHash(any())).thenReturn(Optional.of(tx));

    when(peer1.getConnection())
        .thenReturn(new MockPeerConnection(Set.of(EthProtocol.ETH67), (cap, msg, conn) -> {}));
    when(peer2.getConnection())
        .thenReturn(new MockPeerConnection(Set.of(EthProtocol.ETH67), (cap, msg, conn) -> {}));
  }

  @Test
  public void shouldSendPendingTransactionsToEachPeer() throws Exception {

    transactionTracker.addToPeerHashSendQueue(peer1, transaction1);
    transactionTracker.addToPeerHashSendQueue(peer1, transaction2);
    transactionTracker.addToPeerHashSendQueue(peer2, transaction3);

    List.of(peer1, peer2).forEach(messageSender::sendTransactionHashesToPeer);

    verify(peer1).send(transactionsMessageContaining(transaction1, transaction2));
    verify(peer2).send(transactionsMessageContaining(transaction3));
    verify(peer1).getConnection();
    verify(peer2).getConnection();
    verifyNoMoreInteractions(peer1, peer2);
  }

  @Test
  public void shouldSendTransactionsInBatchesWithLimit() throws Exception {
    final Set<Transaction> transactions =
        generator.transactions(6000).stream().collect(Collectors.toSet());

    transactions.forEach(
        transaction -> transactionTracker.addToPeerHashSendQueue(peer1, transaction));

    messageSender.sendTransactionHashesToPeer(peer1);
    final ArgumentCaptor<MessageData> messageDataArgumentCaptor =
        ArgumentCaptor.forClass(MessageData.class);
    verify(peer1, times(2)).send(messageDataArgumentCaptor.capture());

    final List<MessageData> sentMessages = messageDataArgumentCaptor.getAllValues();

    assertThat(sentMessages)
        .hasSize(2)
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

    assertThat(Sets.union(firstBatch, secondBatch))
        .containsExactlyInAnyOrderElementsOf(toHashList(transactions));
  }

  private MessageData transactionsMessageContaining(final Transaction... transactions) {
    return argThat(
        message -> {
          final Set<Hash> actualSentTransactions = getTransactionsFromMessage(message);
          final Set<Hash> expectedTransactions =
              newHashSet(toHashList(Arrays.asList(transactions)));
          return message.getCode() == EthPV65.NEW_POOLED_TRANSACTION_HASHES
              && actualSentTransactions.equals(expectedTransactions);
        });
  }

  private Set<Hash> getTransactionsFromMessage(final MessageData message) {
    final NewPooledTransactionHashesMessage transactionsMessage =
        NewPooledTransactionHashesMessage.readFrom(message, EthProtocol.ETH66);
    return newHashSet(transactionsMessage.pendingTransactionHashes());
  }
}
