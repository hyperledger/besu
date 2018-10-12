package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.core.SyncStatus;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.SyncingResult;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyncingSubscriptionServiceTest {

  private SyncingSubscriptionService syncingSubscriptionService;

  @Mock private SubscriptionManager subscriptionManager;
  @Mock private Synchronizer synchronizer;

  @Before
  public void before() {
    syncingSubscriptionService = new SyncingSubscriptionService(subscriptionManager, synchronizer);
  }

  @Test
  public void shouldSendSyncStatusWhenSyncing() {
    final SyncingSubscription subscription = new SyncingSubscription(9L, SubscriptionType.SYNCING);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscription));
    final SyncStatus syncStatus = new SyncStatus(0L, 1L, 1L);
    final SyncingResult expectedSyncingResult = new SyncingResult(syncStatus);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));

    syncingSubscriptionService.sendSyncingToMatchingSubscriptions();

    verify(subscriptionManager).sendMessage(eq(subscription.getId()), refEq(expectedSyncingResult));
  }

  @Test
  public void shouldSendFalseWhenNotSyncing() {
    final SyncingSubscription subscription = new SyncingSubscription(9L, SubscriptionType.SYNCING);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscription));
    when(synchronizer.getSyncStatus()).thenReturn(Optional.empty());

    syncingSubscriptionService.sendSyncingToMatchingSubscriptions();

    verify(subscriptionManager)
        .sendMessage(eq(subscription.getId()), refEq(new NotSynchronisingResult()));
  }

  @Test
  public void shouldSendNoMoreSyncStatusWhenSyncingStatusHasNotChanged() {
    final SyncingSubscription subscription = new SyncingSubscription(9L, SubscriptionType.SYNCING);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscription));
    final SyncStatus syncStatus = new SyncStatus(0L, 1L, 1L);
    final SyncingResult expectedSyncingResult = new SyncingResult(syncStatus);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus));

    syncingSubscriptionService.sendSyncingToMatchingSubscriptions();

    verify(subscriptionManager).sendMessage(eq(subscription.getId()), refEq(expectedSyncingResult));
    syncingSubscriptionService.sendSyncingToMatchingSubscriptions();
  }

  @Test
  public void shouldSendDifferentSyncStatusWhenSyncingStatusHasChanged() {
    final SyncingSubscription subscription = new SyncingSubscription(9L, SubscriptionType.SYNCING);
    when(subscriptionManager.subscriptionsOfType(any(), any()))
        .thenReturn(Lists.newArrayList(subscription));
    final SyncStatus syncStatus1 = new SyncStatus(0L, 1L, 9L);
    final SyncStatus syncStatus2 = new SyncStatus(0L, 5L, 9L);
    final SyncingResult expectedSyncingResult1 = new SyncingResult(syncStatus1);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus1));

    syncingSubscriptionService.sendSyncingToMatchingSubscriptions();
    verify(subscriptionManager)
        .sendMessage(eq(subscription.getId()), refEq(expectedSyncingResult1));

    final SyncingResult expectedSyncingResult2 = new SyncingResult(syncStatus2);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(syncStatus2));
    syncingSubscriptionService.sendSyncingToMatchingSubscriptions();
    verify(subscriptionManager)
        .sendMessage(eq(subscription.getId()), refEq(expectedSyncingResult2));
  }
}
