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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Synchronizer.SyncStatusListener;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.SyncingResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyncingSubscriptionServiceTest {

  @Mock private SubscriptionManager subscriptionManager;
  @Mock private Synchronizer synchronizer;
  private SyncStatusListener syncStatusListener;

  @Before
  public void before() {
    final ArgumentCaptor<SyncStatusListener> captor =
        ArgumentCaptor.forClass(SyncStatusListener.class);
    when(synchronizer.observeSyncStatus(captor.capture())).thenReturn(1L);
    new SyncingSubscriptionService(subscriptionManager, synchronizer);
    syncStatusListener = captor.getValue();
  }

  @Test
  public void shouldSendSyncStatusWhenReceiveSyncStatus() {
    final SyncingSubscription subscription = new SyncingSubscription(9L, SubscriptionType.SYNCING);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscription));
    final SyncStatus syncStatus = new SyncStatus(0L, 1L, 3L);
    final SyncingResult expectedSyncingResult = new SyncingResult(syncStatus);

    syncStatusListener.onSyncStatus(syncStatus);

    verify(subscriptionManager).sendMessage(eq(subscription.getId()), eq(expectedSyncingResult));
  }

  @Test
  public void shouldSendNotSyncingStatusWhenReceiveSyncStatusAtHead() {
    final SyncingSubscription subscription = new SyncingSubscription(9L, SubscriptionType.SYNCING);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscription));
    final SyncStatus syncStatus = new SyncStatus(0L, 1L, 1L);

    syncStatusListener.onSyncStatus(syncStatus);

    verify(subscriptionManager)
        .sendMessage(eq(subscription.getId()), any(NotSynchronisingResult.class));
  }
}
