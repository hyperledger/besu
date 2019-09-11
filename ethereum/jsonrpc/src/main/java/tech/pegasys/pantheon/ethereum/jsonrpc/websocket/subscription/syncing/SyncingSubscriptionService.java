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

import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.SyncingResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;
import tech.pegasys.pantheon.plugin.data.SyncStatus;

public class SyncingSubscriptionService {

  private final SubscriptionManager subscriptionManager;

  public SyncingSubscriptionService(
      final SubscriptionManager subscriptionManager, final Synchronizer synchronizer) {
    this.subscriptionManager = subscriptionManager;
    synchronizer.observeSyncStatus(this::sendSyncingToMatchingSubscriptions);
  }

  private void sendSyncingToMatchingSubscriptions(final SyncStatus syncStatus) {
    subscriptionManager.notifySubscribersOnWorkerThread(
        SubscriptionType.SYNCING,
        Subscription.class,
        syncingSubscriptions -> {
          if (syncStatus.inSync()) {
            syncingSubscriptions.forEach(
                s ->
                    subscriptionManager.sendMessage(
                        s.getSubscriptionId(), new NotSynchronisingResult()));
          } else {
            syncingSubscriptions.forEach(
                s ->
                    subscriptionManager.sendMessage(
                        s.getSubscriptionId(), new SyncingResult(syncStatus)));
          }
        });
  }
}
