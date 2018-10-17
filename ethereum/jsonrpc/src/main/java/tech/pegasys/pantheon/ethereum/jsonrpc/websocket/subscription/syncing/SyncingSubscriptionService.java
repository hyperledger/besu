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
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing;

import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.JsonRpcResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.SyncingResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.List;
import java.util.Optional;

public class SyncingSubscriptionService {

  private final SubscriptionManager subscriptionManager;
  private final Synchronizer synchronizer;
  private final long currentRefreshDelay = 5000;

  private Optional<SyncStatus> previousSyncStatus;
  private long timerId;

  public SyncingSubscriptionService(
      final SubscriptionManager subscriptionManager, final Synchronizer synchronizer) {
    this.subscriptionManager = subscriptionManager;
    this.synchronizer = synchronizer;
    previousSyncStatus = synchronizer.getSyncStatus();
    engageNextTimerTick();
  }

  public void sendSyncingToMatchingSubscriptions() {
    final List<Subscription> syncingSubscriptions =
        subscriptionManager.subscriptionsOfType(SubscriptionType.SYNCING, Subscription.class);
    final Optional<SyncStatus> syncStatus = synchronizer.getSyncStatus();

    final boolean syncStatusChange = !syncStatus.equals(previousSyncStatus);
    final JsonRpcResult result;

    if (syncStatus.isPresent()) {
      result = new SyncingResult(syncStatus.get());
    } else {
      result = new NotSynchronisingResult();
    }

    for (final Subscription subscription : syncingSubscriptions) {
      sendSyncingResultToSubscription((SyncingSubscription) subscription, result, syncStatusChange);
    }
    previousSyncStatus = syncStatus;
  }

  private void sendSyncingResultToSubscription(
      final SyncingSubscription subscription,
      final JsonRpcResult result,
      final boolean syncStatusChange) {
    if (syncStatusChange || !subscription.isFirstMessageHasBeenSent()) {
      subscriptionManager.sendMessage(subscription.getId(), result);
      subscription.setFirstMessageHasBeenSent(true);
    }
  }

  public void engageNextTimerTick() {
    if (subscriptionManager.getVertx() != null) {
      this.timerId =
          subscriptionManager
              .getVertx()
              .setTimer(
                  currentRefreshDelay,
                  (id) -> {
                    sendSyncingToMatchingSubscriptions();
                    engageNextTimerTick();
                  });
    }
  }
}
