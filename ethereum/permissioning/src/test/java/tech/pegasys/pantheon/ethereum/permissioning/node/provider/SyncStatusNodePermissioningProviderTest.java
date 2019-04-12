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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Synchronizer.SyncStatusListener;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SyncStatusNodePermissioningProviderTest {

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

    this.provider = new SyncStatusNodePermissioningProvider(synchronizer, bootnodes);
    this.syncStatusListener = captor.getValue();

    verify(synchronizer).observeSyncStatus(any());
  }

  @Test
  public void whenIsNotInSyncHasReachedSyncShouldReturnFalse() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 2));

    assertThat(provider.hasReachedSync()).isFalse();
  }

  @Test
  public void whenInSyncHasReachedSyncShouldReturnTrue() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 1));

    assertThat(provider.hasReachedSync()).isTrue();
  }

  @Test
  public void whenInSyncChangesFromTrueToFalseHasReachedSyncShouldReturnTrue() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();

    syncStatusListener.onSyncStatus(new SyncStatus(0, 2, 1));
    assertThat(provider.hasReachedSync()).isTrue();

    syncStatusListener.onSyncStatus(new SyncStatus(0, 2, 3));
    assertThat(provider.hasReachedSync()).isTrue();
  }

  @Test
  public void whenNotInSyncShouldNotExecuteCallback() {
    final Runnable callbackFunction = mock(Runnable.class);
    provider.setHasReachedSyncCallback(callbackFunction);

    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 2));

    verifyZeroInteractions(callbackFunction);
  }

  @Test
  public void whenInSyncShouldExecuteCallback() {
    final Runnable callbackFunction = mock(Runnable.class);
    provider.setHasReachedSyncCallback(callbackFunction);

    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 1));

    verify(callbackFunction).run();
    // after executing callback, it should unsubscribe from the SyncStatus updates
    verify(synchronizer).removeObserver(eq(syncStatusObserverId));
  }

  @Test
  public void whenHasNotSyncedNonBootnodeShouldNotBePermitted() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();

    boolean isPermitted = provider.isPermitted(enode1, enode2);

    assertThat(isPermitted).isFalse();
  }

  @Test
  public void whenHasNotSyncedBootnodeIncomingConnectionShouldNotBePermitted() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();

    boolean isPermitted = provider.isPermitted(bootnode, enode1);

    assertThat(isPermitted).isFalse();
  }

  @Test
  public void whenHasNotSyncedBootnodeOutgoingConnectionShouldBePermitted() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 2));
    assertThat(provider.hasReachedSync()).isFalse();

    boolean isPermitted = provider.isPermitted(enode1, bootnode);

    assertThat(isPermitted).isTrue();
  }

  @Test
  public void whenHasSyncedIsPermittedShouldReturnTrue() {
    syncStatusListener.onSyncStatus(new SyncStatus(0, 1, 1));
    assertThat(provider.hasReachedSync()).isTrue();

    boolean isPermitted = provider.isPermitted(enode1, enode2);

    assertThat(isPermitted).isTrue();
  }
}
