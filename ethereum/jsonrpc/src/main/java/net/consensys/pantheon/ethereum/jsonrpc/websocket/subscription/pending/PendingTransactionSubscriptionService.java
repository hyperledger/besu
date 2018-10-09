package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.pending;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.PendingTransactionListener;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.List;

public class PendingTransactionSubscriptionService implements PendingTransactionListener {

  private final SubscriptionManager subscriptionManager;

  public PendingTransactionSubscriptionService(final SubscriptionManager subscriptionManager) {
    this.subscriptionManager = subscriptionManager;
  }

  @Override
  public void onTransactionAdded(final Transaction pendingTransaction) {
    notifySubscribers(pendingTransaction.hash());
  }

  private void notifySubscribers(final Hash pendingTransaction) {
    final List<Subscription> subscriptions = pendingTransactionSubscriptions();

    for (final Subscription subscription : subscriptions) {
      subscriptionManager.sendMessage(
          subscription.getId(), new PendingTransactionResult(pendingTransaction));
    }
  }

  private List<Subscription> pendingTransactionSubscriptions() {
    return subscriptionManager.subscriptionsOfType(
        SubscriptionType.NEW_PENDING_TRANSACTIONS, Subscription.class);
  }
}
