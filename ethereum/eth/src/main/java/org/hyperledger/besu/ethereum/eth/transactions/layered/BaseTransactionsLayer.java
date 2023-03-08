package org.hyperledger.besu.ethereum.eth.transactions.layered;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.util.Subscribers;

import java.util.OptionalLong;

public abstract class BaseTransactionsLayer implements TransactionsLayer {

  protected final TransactionsLayer nextLayer;
  private final Subscribers<PendingTransactionAddedListener> onAddedListeners =
      Subscribers.create();

  private final Subscribers<PendingTransactionDroppedListener> onDroppedListeners =
      Subscribers.create();

  private OptionalLong nextLayerOnAddedListenerId = OptionalLong.empty();
  private OptionalLong nextLayerOnDroppedListenerId = OptionalLong.empty();

  public BaseTransactionsLayer(final TransactionsLayer nextLayer) {
    this.nextLayer = nextLayer;
  }

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
    nextLayerOnAddedListenerId = OptionalLong.of(nextLayer.subscribeToAdded(listener));
    return onAddedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeFromAdded(final long id) {
    nextLayerOnAddedListenerId.ifPresent(nextLayer::unsubscribeFromAdded);
    onAddedListeners.unsubscribe(id);
  }

  @Override
  public long subscribeToDropped(final PendingTransactionDroppedListener listener) {
    nextLayerOnDroppedListenerId = OptionalLong.of(nextLayer.subscribeToDropped(listener));
    return onDroppedListeners.subscribe(listener);
  }

  @Override
  public void unsubscribeFromDropped(final long id) {
    nextLayerOnDroppedListenerId.ifPresent(nextLayer::unsubscribeFromDropped);
    onDroppedListeners.unsubscribe(id);
  }
}
