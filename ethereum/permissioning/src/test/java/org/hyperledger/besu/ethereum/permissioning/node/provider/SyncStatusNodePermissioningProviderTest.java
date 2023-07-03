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
package org.hyperledger.besu.ethereum.permissioning.node.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.core.Synchronizer.InSyncListener;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.IntSupplier;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SyncStatusNodePermissioningProviderTest {
  @Mock private MetricsSystem metricsSystem;
  @Mock private Counter checkCounter;
  @Mock private Counter checkPermittedCounter;
  @Mock private Counter checkUnpermittedCounter;
  private IntSupplier syncGauge;

  private static final EnodeURL bootnode =
      EnodeURLImpl.fromString(
          "enode://6332792c4a00e3e4ee0926ed89e0d27ef985424d97b6a45bf0f23e51f0dcb5e66b875777506458aea7af6f9e4ffb69f43f3778ee73c81ed9d34c51c4b16b0b0f@192.168.0.1:9999");
  private static final EnodeURL enode1 =
      EnodeURLImpl.fromString(
          "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.168.0.2:1234");
  private static final EnodeURL enode2 =
      EnodeURLImpl.fromString(
          "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678");

  @Mock private Synchronizer synchronizer;
  private final Collection<EnodeURL> bootnodes = new ArrayList<>();
  private SyncStatusNodePermissioningProvider provider;
  private InSyncListener inSyncListener;

  @BeforeEach
  public void before() {
    final ArgumentCaptor<InSyncListener> inSyncSubscriberCaptor =
        ArgumentCaptor.forClass(InSyncListener.class);
    final ArgumentCaptor<Long> syncToleranceCaptor = ArgumentCaptor.forClass(Long.class);
    when(synchronizer.subscribeInSync(
            inSyncSubscriberCaptor.capture(), syncToleranceCaptor.capture()))
        .thenReturn(1L);
    bootnodes.add(bootnode);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<IntSupplier> syncGaugeCallbackCaptor =
        ArgumentCaptor.forClass(IntSupplier.class);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "sync_status_node_check_count",
            "Number of times the sync status permissioning provider has been checked"))
        .thenReturn(checkCounter);
    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "sync_status_node_check_count_permitted",
            "Number of times the sync status permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);
    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "sync_status_node_check_count_unpermitted",
            "Number of times the sync status permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);
    this.provider = new SyncStatusNodePermissioningProvider(synchronizer, bootnodes, metricsSystem);
    this.inSyncListener = inSyncSubscriberCaptor.getValue();
    assertThat(syncToleranceCaptor.getValue()).isEqualTo(0);
    verify(metricsSystem)
        .createIntegerGauge(
            eq(BesuMetricCategory.PERMISSIONING),
            eq("sync_status_node_sync_reached"),
            eq("Whether the sync status permissioning provider has realised sync yet"),
            syncGaugeCallbackCaptor.capture());
    this.syncGauge = syncGaugeCallbackCaptor.getValue();

    verify(synchronizer).subscribeInSync(any(), eq(0L));
  }

  @Test
  public void whenIsNotInSyncHasReachedSyncShouldReturnFalse() {
    inSyncListener.onInSyncStatusChange(false);

    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);
  }

  @Test
  public void whenInSyncHasReachedSyncShouldReturnTrue() {
    inSyncListener.onInSyncStatusChange(true);

    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);
  }

  @Test
  public void whenInSyncChangesFromTrueToFalseHasReachedSyncShouldReturnTrue() {
    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    inSyncListener.onInSyncStatusChange(true);
    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);

    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);
  }

  @Test
  public void whenHasNotSyncedNonBootnodeShouldNotBePermitted() {
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isConnectionPermitted(enode1, enode2);

    assertThat(isPermitted).isFalse();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  @Test
  public void whenHasNotSyncedBootnodeIncomingConnectionShouldNotBePermitted() {
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isConnectionPermitted(bootnode, enode1);

    assertThat(isPermitted).isFalse();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  @Test
  public void whenHasNotSyncedBootnodeOutgoingConnectionShouldBePermitted() {
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isConnectionPermitted(enode1, bootnode);

    assertThat(isPermitted).isTrue();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(1)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  @Test
  public void whenOutOfSyncNonBootnodeShouldNotBePermitted() {
    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isConnectionPermitted(enode1, enode2);

    assertThat(isPermitted).isFalse();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  @Test
  public void whenOutOfSyncBootnodeIncomingConnectionShouldNotBePermitted() {
    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isConnectionPermitted(bootnode, enode1);

    assertThat(isPermitted).isFalse();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }

  @Test
  public void whenOutOfSyncBootnodeOutgoingConnectionShouldBePermitted() {
    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isFalse();
    assertThat(syncGauge.getAsInt()).isEqualTo(0);

    boolean isPermitted = provider.isConnectionPermitted(enode1, bootnode);

    assertThat(isPermitted).isTrue();
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(1)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  @Test
  public void whenHasSyncedIsPermittedShouldReturnTrue() {
    inSyncListener.onInSyncStatusChange(true);
    assertThat(provider.hasReachedSync()).isTrue();
    assertThat(syncGauge.getAsInt()).isEqualTo(1);

    boolean isPermitted = provider.isConnectionPermitted(enode1, enode2);

    assertThat(isPermitted).isTrue();
    verify(checkCounter, times(0)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  @Test
  public void syncStatusPermissioningCheckShouldIgnoreEnodeURLDiscoveryPort() {
    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isFalse();

    final EnodeURL bootnode =
        EnodeURLImpl.fromString(
            "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678");
    final EnodeURL enodeWithDiscoveryPort =
        EnodeURLImpl.fromString(
            "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678?discport=30303");

    final SyncStatusNodePermissioningProvider provider =
        new SyncStatusNodePermissioningProvider(
            synchronizer, Lists.newArrayList(bootnode), metricsSystem);

    boolean isPermitted = provider.isConnectionPermitted(enode1, enodeWithDiscoveryPort);

    assertThat(isPermitted).isTrue();
  }

  @Test
  public void syncStatusPermissioningCheckShouldAllowDNS() throws UnknownHostException {
    inSyncListener.onInSyncStatusChange(false);
    assertThat(provider.hasReachedSync()).isFalse();

    EnodeURL bootnode =
        EnodeURLImpl.builder()
            .nodeId(
                "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0")
            .listeningPort(9999)
            .disableDiscovery()
            .ipAddress(
                InetAddress.getLocalHost().getCanonicalHostName(),
                ImmutableEnodeDnsConfiguration.builder()
                    .dnsEnabled(true)
                    .updateEnabled(true)
                    .build())
            .build();

    EnodeURL enodeWithIP =
        EnodeURLImpl.builder()
            .nodeId(
                "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0")
            .listeningPort(9999)
            .disableDiscovery()
            .ipAddress(
                InetAddress.getLocalHost().getHostAddress(),
                ImmutableEnodeDnsConfiguration.builder()
                    .dnsEnabled(true)
                    .updateEnabled(true)
                    .build())
            .build();

    final SyncStatusNodePermissioningProvider provider =
        new SyncStatusNodePermissioningProvider(
            synchronizer, Lists.newArrayList(bootnode), metricsSystem);

    boolean isPermitted = provider.isConnectionPermitted(enode1, enodeWithIP);

    assertThat(isPermitted).isTrue();
  }
}
