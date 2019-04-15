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
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.pending;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactionListener;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

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

    final PendingTransactionResult msg = new PendingTransactionResult(pendingTransaction);
    for (final Subscription subscription : subscriptions) {
      subscriptionManager.sendMessage(subscription.getId(), msg);
    }
  }

  private List<Subscription> pendingTransactionSubscriptions() {
    return subscriptionManager.subscriptionsOfType(
        SubscriptionType.NEW_PENDING_TRANSACTIONS, Subscription.class);
  }
}
