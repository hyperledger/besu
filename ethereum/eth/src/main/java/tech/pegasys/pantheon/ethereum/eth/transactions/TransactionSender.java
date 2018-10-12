package tech.pegasys.pantheon.ethereum.eth.transactions;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionPool.TransactionBatchAddedListener;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;

class TransactionSender implements TransactionBatchAddedListener {

  private final PeerTransactionTracker transactionTracker;
  private final TransactionsMessageSender transactionsMessageSender;
  private final EthContext ethContext;

  public TransactionSender(
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final EthContext ethContext) {
    this.transactionTracker = transactionTracker;
    this.transactionsMessageSender = transactionsMessageSender;
    this.ethContext = ethContext;
  }

  @Override
  public void onTransactionsAdded(final Iterable<Transaction> transactions) {
    ethContext
        .getEthPeers()
        .availablePeers()
        .forEach(
            peer ->
                transactions.forEach(
                    transaction -> transactionTracker.addToPeerSendQueue(peer, transaction)));
    ethContext
        .getScheduler()
        .scheduleWorkerTask(transactionsMessageSender::sendTransactionsToPeers);
  }
}
