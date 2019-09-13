/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.pending;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.Subscription;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactionListener;

import java.util.List;

public class PendingTransactionSubscriptionService implements PendingTransactionListener {

  private final SubscriptionManager subscriptionManager;

  public PendingTransactionSubscriptionService(final SubscriptionManager subscriptionManager) {
    this.subscriptionManager = subscriptionManager;
  }

  @Override
  public void onTransactionAdded(final Transaction pendingTransaction) {
    notifySubscribers(pendingTransaction);
  }

  private void notifySubscribers(final Transaction pendingTransaction) {
    final List<Subscription> subscriptions = pendingTransactionSubscriptions();

    final PendingTransactionResult hashResult =
        new PendingTransactionResult(pendingTransaction.hash());
    final PendingTransactionDetailResult detailResult =
        new PendingTransactionDetailResult(pendingTransaction);
    for (final Subscription subscription : subscriptions) {
      if (Boolean.TRUE.equals(subscription.getIncludeTransaction())) {
        subscriptionManager.sendMessage(subscription.getSubscriptionId(), detailResult);
      } else {
        subscriptionManager.sendMessage(subscription.getSubscriptionId(), hashResult);
      }
    }
  }

  private List<Subscription> pendingTransactionSubscriptions() {
    return subscriptionManager.subscriptionsOfType(
        SubscriptionType.NEW_PENDING_TRANSACTIONS, Subscription.class);
  }
}
