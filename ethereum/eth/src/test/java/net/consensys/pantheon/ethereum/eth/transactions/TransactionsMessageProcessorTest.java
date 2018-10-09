package net.consensys.pantheon.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.messages.TransactionsMessage;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class TransactionsMessageProcessorTest {

  private final TransactionPool transactionPool = mock(TransactionPool.class);
  private final PeerTransactionTracker transactionTracker = mock(PeerTransactionTracker.class);
  private final EthPeer peer1 = mock(EthPeer.class);

  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  private final TransactionsMessageProcessor messageHandler =
      new TransactionsMessageProcessor(transactionTracker, transactionPool);

  @Test
  public void shouldMarkAllReceivedTransactionsAsSeen() {
    messageHandler.processTransactionsMessage(
        peer1, TransactionsMessage.create(asList(transaction1, transaction2, transaction3)));

    verify(transactionTracker)
        .markTransactionsAsSeen(peer1, ImmutableSet.of(transaction1, transaction2, transaction3));
  }

  @Test
  public void shouldAddReceivedTransactionsToTransactionPool() {
    messageHandler.processTransactionsMessage(
        peer1, TransactionsMessage.create(asList(transaction1, transaction2, transaction3)));

    verify(transactionPool)
        .addRemoteTransactions(ImmutableSet.of(transaction1, transaction2, transaction3));
  }
}
