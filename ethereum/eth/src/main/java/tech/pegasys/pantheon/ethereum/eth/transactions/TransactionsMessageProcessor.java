package tech.pegasys.pantheon.ethereum.eth.transactions;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.messages.TransactionsMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;

import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.Logger;

class TransactionsMessageProcessor {

  private static final Logger LOG = getLogger();
  private final PeerTransactionTracker transactionTracker;
  private final TransactionPool transactionPool;

  public TransactionsMessageProcessor(
      final PeerTransactionTracker transactionTracker, final TransactionPool transactionPool) {
    this.transactionTracker = transactionTracker;
    this.transactionPool = transactionPool;
  }

  void processTransactionsMessage(
      final EthPeer peer, final TransactionsMessage transactionsMessage) {
    try {
      LOG.debug("Received transactions message from {}", peer);

      final Iterator<Transaction> readTransactions =
          transactionsMessage.transactions(Transaction::readFrom);
      final Set<Transaction> transactions = Sets.newHashSet(readTransactions);
      transactionTracker.markTransactionsAsSeen(peer, transactions);
      transactionPool.addRemoteTransactions(transactions);
    } catch (final RLPException ex) {
      if (peer != null) {
        peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      }
    } finally {
      transactionsMessage.release();
    }
  }
}
