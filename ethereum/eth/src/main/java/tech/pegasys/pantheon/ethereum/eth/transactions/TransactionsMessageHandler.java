package tech.pegasys.pantheon.ethereum.eth.transactions;

import tech.pegasys.pantheon.ethereum.eth.manager.EthMessage;
import tech.pegasys.pantheon.ethereum.eth.manager.EthMessages.MessageCallback;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.messages.TransactionsMessage;

class TransactionsMessageHandler implements MessageCallback {

  private final TransactionsMessageProcessor transactionsMessageProcessor;
  private final EthScheduler scheduler;

  public TransactionsMessageHandler(
      final EthScheduler scheduler,
      final TransactionsMessageProcessor transactionsMessageProcessor) {
    this.scheduler = scheduler;
    this.transactionsMessageProcessor = transactionsMessageProcessor;
  }

  @Override
  public void exec(final EthMessage message) {
    final TransactionsMessage transactionsMessage = TransactionsMessage.readFrom(message.getData());
    scheduler.scheduleWorkerTask(
        () ->
            transactionsMessageProcessor.processTransactionsMessage(
                message.getPeer(), transactionsMessage));
  }
}
