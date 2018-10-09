package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.logs;

import net.consensys.pantheon.ethereum.chain.BlockAddedEvent;
import net.consensys.pantheon.ethereum.chain.BlockAddedObserver;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Log;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.LogWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.LogResult;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.List;
import java.util.Optional;

public class LogsSubscriptionService implements BlockAddedObserver {

  private final SubscriptionManager subscriptionManager;
  private final BlockchainQueries blockchainQueries;

  public LogsSubscriptionService(
      final SubscriptionManager subscriptionManager, final BlockchainQueries blockchainQueries) {
    this.subscriptionManager = subscriptionManager;
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    final List<LogsSubscription> logsSubscriptions =
        subscriptionManager.subscriptionsOfType(SubscriptionType.LOGS, LogsSubscription.class);

    if (logsSubscriptions.isEmpty()) {
      return;
    }

    event
        .getAddedTransactions()
        .stream()
        .map(tx -> blockchainQueries.transactionReceiptByTransactionHash(tx.hash()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEachOrdered(
            receiptWithMetadata -> {
              final List<Log> logs = receiptWithMetadata.getReceipt().getLogs();
              sendLogsToMatchingSubscriptions(logs, logsSubscriptions, receiptWithMetadata, false);
            });

    event
        .getRemovedTransactions()
        .stream()
        .map(tx -> blockchainQueries.transactionReceiptByTransactionHash(tx.hash()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEachOrdered(
            receiptWithMetadata -> {
              final List<Log> logs = receiptWithMetadata.getReceipt().getLogs();
              sendLogsToMatchingSubscriptions(logs, logsSubscriptions, receiptWithMetadata, true);
            });
  }

  private void sendLogsToMatchingSubscriptions(
      final List<Log> logs,
      final List<LogsSubscription> logsSubscriptions,
      final TransactionReceiptWithMetadata receiptWithMetadata,
      final boolean removed) {
    for (int logIndex = 0; logIndex < logs.size(); logIndex++) {
      for (final LogsSubscription subscription : logsSubscriptions) {
        if (subscription.getLogsQuery().matches(logs.get(logIndex))) {
          sendLogToSubscription(receiptWithMetadata, removed, logIndex, subscription);
        }
      }
    }
  }

  private void sendLogToSubscription(
      final TransactionReceiptWithMetadata receiptWithMetadata,
      final boolean removed,
      final int logIndex,
      final LogsSubscription subscription) {
    final LogWithMetadata logWithMetaData = logWithMetadata(logIndex, receiptWithMetadata, removed);
    subscriptionManager.sendMessage(subscription.getId(), new LogResult(logWithMetaData));
  }

  // @formatter:off
  private LogWithMetadata logWithMetadata(
      final int logIndex,
      final TransactionReceiptWithMetadata transactionReceiptWithMetadata,
      final boolean removed) {
    return LogWithMetadata.create(
        logIndex,
        transactionReceiptWithMetadata.getBlockNumber(),
        transactionReceiptWithMetadata.getBlockHash(),
        transactionReceiptWithMetadata.getTransactionHash(),
        transactionReceiptWithMetadata.getTransactionIndex(),
        transactionReceiptWithMetadata.getReceipt().getLogs().get(logIndex).getLogger(),
        transactionReceiptWithMetadata.getReceipt().getLogs().get(logIndex).getData(),
        transactionReceiptWithMetadata.getReceipt().getLogs().get(logIndex).getTopics(),
        removed);
  }
  // @formatter:on
}
