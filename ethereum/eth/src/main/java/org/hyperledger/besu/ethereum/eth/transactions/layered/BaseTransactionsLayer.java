package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.util.Subscribers;

public abstract class BaseTransactionsLayer implements TransactionsLayer {

  private final Subscribers<PendingTransactionAddedListener> onAddedListeners =
      Subscribers.create();

  private final Subscribers<PendingTransactionDroppedListener> onDroppedListeners =
      Subscribers.create();

  protected void notifyTransactionAdded(final PendingTransaction pendingTransaction) {
    onAddedListeners.forEach(
        listener -> listener.onTransactionAdded(pendingTransaction.getTransaction()));
  }

  protected void notifyTransactionDropped(final PendingTransaction pendingTransaction) {
    onDroppedListeners.forEach(
        listener -> listener.onTransactionDropped(pendingTransaction.getTransaction()));
  }

  @Override
  public long subscribeToAdded(final PendingTransactionAddedListener listener) {
    return onAddedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeFromAdded(final long id) {
    onAddedListeners.unsubscribe(id);
  }

  @Override
  public long subscribeToDropped(final PendingTransactionDroppedListener listener) {
    return onDroppedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeFromDropped(final long id) {
    onDroppedListeners.unsubscribe(id);
  }
}
