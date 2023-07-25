/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.syncing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.SyncingResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.ethereum.core.DefaultSyncStatus;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.BesuEvents.SyncStatusListener;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SyncingSubscriptionServiceTest {

  @Mock private SubscriptionManager subscriptionManager;
  @Mock private Synchronizer synchronizer;
  private SyncStatusListener syncStatusListener;

  @BeforeEach
  public void before() {
    final ArgumentCaptor<SyncStatusListener> captor =
        ArgumentCaptor.forClass(SyncStatusListener.class);
    when(synchronizer.subscribeSyncStatus(captor.capture())).thenReturn(1L);
    new SyncingSubscriptionService(subscriptionManager, synchronizer);
    syncStatusListener = captor.getValue();
  }

  @Test
  public void shouldSendSyncStatusWhenReceiveSyncStatus() {
    final SyncingSubscription subscription =
        new SyncingSubscription(9L, "conn", SubscriptionType.SYNCING);
    final List<SyncingSubscription> subscriptions = Collections.singletonList(subscription);
    final Optional<SyncStatus> syncStatus =
        Optional.of(new DefaultSyncStatus(0L, 1L, 3L, Optional.empty(), Optional.empty()));
    final JsonRpcResult expectedSyncingResult = new SyncingResult(syncStatus.get());

    doAnswer(
            invocation -> {
              Consumer<List<SyncingSubscription>> consumer = invocation.getArgument(2);
              consumer.accept(subscriptions);
              return null;
            })
        .when(subscriptionManager)
        .notifySubscribersOnWorkerThread(any(), any(), any());

    syncStatusListener.onSyncStatusChanged(syncStatus);

    verify(subscriptionManager)
        .sendMessage(
            ArgumentMatchers.eq(subscription.getSubscriptionId()), eq(expectedSyncingResult));
  }

  @Test
  public void shouldSendNotSyncingResultWhenReceiveNonSyncingStatus() {
    final SyncingSubscription subscription =
        new SyncingSubscription(9L, "conn", SubscriptionType.SYNCING);
    final List<SyncingSubscription> subscriptions = Collections.singletonList(subscription);
    final Optional<SyncStatus> syncStatus = Optional.empty();
    final JsonRpcResult expectedSyncingResult = new NotSynchronisingResult();

    doAnswer(
            invocation -> {
              Consumer<List<SyncingSubscription>> consumer = invocation.getArgument(2);
              consumer.accept(subscriptions);
              return null;
            })
        .when(subscriptionManager)
        .notifySubscribersOnWorkerThread(any(), any(), any());

    syncStatusListener.onSyncStatusChanged(syncStatus);

    verify(subscriptionManager)
        .sendMessage(
            ArgumentMatchers.eq(subscription.getSubscriptionId()), eq(expectedSyncingResult));
  }
}
