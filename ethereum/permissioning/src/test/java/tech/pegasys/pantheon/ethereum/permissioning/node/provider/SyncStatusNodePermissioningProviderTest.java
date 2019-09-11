/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.permissioning.node.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.PantheonEvents.SyncStatusListener;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.IntSupplier;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyncStatusNodePermissioningProviderTest {
  @Mock private MetricsSystem metricsSystem;
  @Mock private Counter checkCounter;
  @Mock private Counter checkPermittedCounter;
  @Mock private Counter checkUnpermittedCounter;
  private IntSupplier syncGauge;

  private static final EnodeURL bootnode =
      EnodeURL.fromString(
          "enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@192.168.0.1:9999");
  private static final EnodeURL enode1 =
      EnodeURL.fromString(
          "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.168.0.2:1234");
  private static final EnodeURL enode2 =
      EnodeURL.fromString(
          "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678");

  @Mock private Synchronizer synchronizer;
  private Collection<EnodeURL> bootnodes = new ArrayList<>();
  private SyncStatusNodePermissioningProvider provider;
  private SyncStatusListener syncStatusListener;
  private long syncStatusObserverId = 1L;

  @Before
  public void before() {
    final ArgumentCaptor<SyncStatusListener> captor =
        ArgumentCaptor.forClass(SyncStatusListener.class);
    when(synchronizer.observeSyncStatus(captor.capture())).thenReturn(syncStatusObserverId);
    bootnodes.add(bootnode);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<IntSupplier> syncGaugeCallbackCaptor =
        ArgumentCaptor.forClass(IntSupplier.class);

    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "sync_status_node_check_count",
            "Number of times the sync status permissioning provider has been checked"))
        .thenReturn(checkCounter);
    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "sync_status_node_check_count_permitted",
            "Number of times the sync status permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);
    when(metricsSystem.createCounter(
            PantheonMetricCategory.PERMISSIONING,
            "sync_status_node_check_count_unpermitted",
            "Number of times the sync status permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);
    this.provider = new SyncStatusNodePermissioningProvider(synchronizer, bootnodes, metricsSystem);
    this.syncStatusListener = captor.getValue();
    verify(metricsSystem)
        .createIntegerGauge(
            eq(PantheonMetricCategory.PERMISSIONING),
            eq("sync_status_node_sync_reached"),
            eq("Whether the sync status permissioning provider has realised sync yet"),
            syncGaugeCallbackCaptor.capture());
    this.syncGauge = syncGaugeCallbackCaptor.getValue();

    verify(synchronizer).observeSyncStatus(any());
  }

  @Test
  public void whenIsNotInSyncHasReachedSyncShouldReturnFalse() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 2));

    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);
  }

  @Test
  public void whenInSyncHasReachedSyncShouldReturnTrue() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 1));

    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);
  }

  @Test
  public void whenInSyncChangesFromTrueToFalseHasReachedSyncShouldReturnTrue() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 2, 1));
    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);

    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 2, 3));
    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);
  }

  @Test
  public void whenHasNotSyncedNonBootnodeShouldNotBePermitted() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isPermitted(enode1, enode2);

    assertThat(isPermitted).isFalse();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  @Test
  public void whenHasNotSyncedBootnodeIncomingConnectionShouldNotBePermitted() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isPermitted(bootnode, enode1);

    assertThat(isPermitted).isFalse();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  @Test
  public void whenHasNotSyncedBootnodeOutgoingConnectionShouldBePermitted() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isPermitted(enode1, bootnode);

    assertThat(isPermitted).isTrue();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(1)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  @Test
  public void whenHasSyncedIsPermittedShouldReturnTrue() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 1));
    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);

    boolean isPermitted = provider.isPermitted(enode1, enode2);

    assertThat(isPermitted).isTrue();
    verify(checkCounter, times(0)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  @Test
  public void syncStatusPermissioningCheckShouldIgnoreEnodeURLDiscoveryPort() {
    syncStatusListener.onSyncStatusChanged(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();

    final EnodeURL bootnode =
        EnodeURL.fromString(
            "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678");
    final EnodeURL enodeWithDiscoveryPort =
        EnodeURL.fromString(
            "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678?discport=30303");

    final SyncStatusNodePermissioningProvider provider =
        new SyncStatusNodePermissioningProvider(
            synchronizer, Lists.newArrayList(bootnode), metricsSystem);

    boolean isPermitted = provider.isPermitted(enode1, enodeWithDiscoveryPort);

    assertThat(isPermitted).isTrue();
  }
}
