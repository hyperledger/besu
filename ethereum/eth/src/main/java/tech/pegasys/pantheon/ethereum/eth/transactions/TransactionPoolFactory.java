package tech.pegasys.pantheon.ethereum.eth.transactions;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

public class TransactionPoolFactory {

  public static TransactionPool createTransactionPool(
      final ProtocolSchedule<?> protocolSchedule,
      final ProtocolContext<?> protocolContext,
      final EthContext ethContext) {
    final PendingTransactions pendingTransactions =
        new PendingTransactions(PendingTransactions.MAX_PENDING_TRANSACTIONS);

    final PeerTransactionTracker transactionTracker = new PeerTransactionTracker();
    final TransactionsMessageSender transactionsMessageSender =
        new TransactionsMessageSender(transactionTracker);

    final TransactionPool transactionPool =
        new TransactionPool(
            pendingTransactions,
            protocolSchedule,
            protocolContext,
            new TransactionSender(transactionTracker, transactionsMessageSender, ethContext));

    final TransactionsMessageHandler transactionsMessageHandler =
        new TransactionsMessageHandler(
            ethContext.getScheduler(),
            new TransactionsMessageProcessor(transactionTracker, transactionPool));

    ethContext.getEthMessages().subscribe(EthPV62.TRANSACTIONS, transactionsMessageHandler);
    protocolContext.getBlockchain().observeBlockAdded(transactionPool);
    ethContext.getEthPeers().subscribeDisconnect(transactionTracker);
    return transactionPool;
  }
}
