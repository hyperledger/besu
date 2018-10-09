package net.consensys.pantheon.ethereum.eth.transactions;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.messages.EthPV62;
import net.consensys.pantheon.ethereum.eth.messages.TransactionsMessage;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

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
  public void shouldSendTransactionsInBatches() throws Exception {
    final Set<Transaction> fifteenTransactions =
        IntStream.range(0, 15).mapToObj(number -> generator.transaction()).collect(toSet());
    fifteenTransactions.forEach(
        transaction -> transactionTracker.addToPeerSendQueue(peer1, transaction));

    messageSender.sendTransactionsToPeers();

    final ArgumentCaptor<MessageData> messageDataArgumentCaptor =
        ArgumentCaptor.forClass(MessageData.class);
    verify(peer1, times(2)).send(messageDataArgumentCaptor.capture());

    final List<MessageData> sentMessages = messageDataArgumentCaptor.getAllValues();

    assertThat(sentMessages).hasSize(2);
    assertThat(sentMessages).allMatch(message -> message.getCode() == EthPV62.TRANSACTIONS);
    final Set<Transaction> firstBatch = getTransactionsFromMessage(sentMessages.get(0));
    final Set<Transaction> secondBatch = getTransactionsFromMessage(sentMessages.get(1));

    assertThat(firstBatch).hasSize(10);
    assertThat(secondBatch).hasSize(5);

    assertThat(Sets.union(firstBatch, secondBatch)).isEqualTo(fifteenTransactions);
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
    return newHashSet(transactionsMessage.transactions(Transaction::readFrom));
  }
}
