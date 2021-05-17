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
package org.hyperledger.besu.ethereum.permissioning.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.permissioning.GoQuorumQip714Gate;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NodePermissioningControllerTest {

  private static final EnodeURL enode1 =
      EnodeURLImpl.fromString(
          "enode://94c15d1b9e2fe7ce56e458b9a3b672ef11894ddedd0c6f247e0f1d3487f52b66208fb4aeb8179fce6e3a749ea93ed147c37976d67af557508d199d9594c35f09@192.168.0.2:1234");
  private static final EnodeURL enode2 =
      EnodeURLImpl.fromString(
          "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.3:5678");

  @Mock private SyncStatusNodePermissioningProvider syncStatusNodePermissioningProvider;
  Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProviderOptional;
  @Mock private NodeLocalConfigPermissioningController localConfigNodePermissioningProvider;
  @Mock private NodeConnectionPermissioningProvider otherPermissioningProvider;
  @Mock private GoQuorumQip714Gate goQuorumQip714Gate;

  private NodePermissioningController controller;

  @Before
  public void before() {
    syncStatusNodePermissioningProviderOptional = Optional.of(syncStatusNodePermissioningProvider);
    List<NodeConnectionPermissioningProvider> emptyProviders = new ArrayList<>();
    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional, emptyProviders, Optional.empty());
  }

  @Test
  public void isPermittedShouldDelegateToSyncStatusProvider() {
    controller.isPermitted(enode1, enode2);

    verify(syncStatusNodePermissioningProvider, atLeast(1))
        .isConnectionPermitted(eq(enode1), eq(enode2));
  }

  @Test
  public void whenNoSyncStatusProviderWeShouldDelegateToLocalConfigNodePermissioningProvider() {
    List<NodeConnectionPermissioningProvider> providers = new ArrayList<>();
    providers.add(localConfigNodePermissioningProvider);
    this.controller =
        new NodePermissioningController(Optional.empty(), providers, Optional.empty());

    controller.isPermitted(enode1, enode2);

    verify(localConfigNodePermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
  }

  @Test
  public void
      whenInSyncWeShouldDelegateToAnyOtherNodePermissioningProviderAndIsPermittedIfAllPermitted() {
    List<NodeConnectionPermissioningProvider> providers = getNodePermissioningProviders();
    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional, providers, Optional.empty());

    when(syncStatusNodePermissioningProvider.isConnectionPermitted(eq(enode1), eq(enode2)))
        .thenReturn(true);
    when(syncStatusNodePermissioningProvider.hasReachedSync()).thenReturn(true);
    when(localConfigNodePermissioningProvider.isConnectionPermitted(eq(enode1), eq(enode2)))
        .thenReturn(true);
    when(otherPermissioningProvider.isConnectionPermitted(eq(enode1), eq(enode2))).thenReturn(true);

    assertThat(controller.isPermitted(enode1, enode2)).isTrue();

    verify(syncStatusNodePermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
    verify(localConfigNodePermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
    verify(otherPermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
  }

  private List<NodeConnectionPermissioningProvider> getNodePermissioningProviders() {
    List<NodeConnectionPermissioningProvider> providers = new ArrayList<>();
    providers.add(localConfigNodePermissioningProvider);
    providers.add(otherPermissioningProvider);
    return providers;
  }

  @Test
  public void
      whenInSyncWeShouldDelegateToAnyOtherNodePermissioningProviderAndIsNotPermittedIfAnyNotPermitted() {
    List<NodeConnectionPermissioningProvider> providers = getNodePermissioningProviders();

    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional, providers, Optional.empty());

    when(syncStatusNodePermissioningProvider.isConnectionPermitted(eq(enode1), eq(enode2)))
        .thenReturn(true);
    when(syncStatusNodePermissioningProvider.hasReachedSync()).thenReturn(true);
    when(localConfigNodePermissioningProvider.isConnectionPermitted(eq(enode1), eq(enode2)))
        .thenReturn(true);
    when(otherPermissioningProvider.isConnectionPermitted(eq(enode1), eq(enode2)))
        .thenReturn(false);

    assertThat(controller.isPermitted(enode1, enode2)).isFalse();

    verify(syncStatusNodePermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
    verify(localConfigNodePermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
    verify(otherPermissioningProvider).isConnectionPermitted(eq(enode1), eq(enode2));
  }

  @Test
  public void shouldStopAtInsufficientPeersWhenInsufficientAndBootnode() {
    final List<NodeConnectionPermissioningProvider> providers = getNodePermissioningProviders();

    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional, providers, Optional.empty());

    final ContextualNodePermissioningProvider insufficientPeersPermissioningProvider =
        mock(ContextualNodePermissioningProvider.class);

    when(syncStatusNodePermissioningProvider.hasReachedSync()).thenReturn(true);
    when(insufficientPeersPermissioningProvider.isPermitted(eq(enode1), eq(enode2)))
        .thenReturn(Optional.of(true));

    controller.setInsufficientPeersPermissioningProvider(insufficientPeersPermissioningProvider);

    assertThat(controller.isPermitted(enode1, enode2)).isTrue();

    verify(insufficientPeersPermissioningProvider, times(1)).isPermitted(any(), any());
    providers.forEach(p -> verify(p, times(0)).isConnectionPermitted(any(), any()));
  }

  @Test
  public void doesntStopAtInsufficientPeersWhenNoAnswer() {
    final List<NodeConnectionPermissioningProvider> providers = getNodePermissioningProviders();

    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional, providers, Optional.empty());

    final ContextualNodePermissioningProvider insufficientPeersPermissioningProvider =
        mock(ContextualNodePermissioningProvider.class);

    when(syncStatusNodePermissioningProvider.isConnectionPermitted(any(), any())).thenReturn(true);
    when(syncStatusNodePermissioningProvider.hasReachedSync()).thenReturn(true);
    when(insufficientPeersPermissioningProvider.isPermitted(any(), any()))
        .thenReturn(Optional.empty());
    when(localConfigNodePermissioningProvider.isConnectionPermitted(any(), any())).thenReturn(true);
    when(otherPermissioningProvider.isConnectionPermitted(any(), any())).thenReturn(true);

    controller.setInsufficientPeersPermissioningProvider(insufficientPeersPermissioningProvider);

    assertThat(controller.isPermitted(enode1, enode2)).isTrue();

    verify(syncStatusNodePermissioningProvider, times(1)).isConnectionPermitted(any(), any());
    verify(insufficientPeersPermissioningProvider, times(1)).isPermitted(any(), any());
    providers.forEach(p -> verify(p, times(1)).isConnectionPermitted(any(), any()));
  }

  @Test
  public void whenQuorumQip714GateIsEmptyShouldDelegateToProviders() {
    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional, Collections.emptyList(), Optional.empty());

    controller.isPermitted(enode1, enode2);

    verify(syncStatusNodePermissioningProvider, atLeast(1))
        .isConnectionPermitted(eq(enode1), eq(enode2));
  }

  @Test
  public void whenQuorumQip714GateIsNotActiveShouldBypassProviders() {
    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional,
            Collections.emptyList(),
            Optional.of(goQuorumQip714Gate));

    when(goQuorumQip714Gate.shouldCheckPermissions()).thenReturn(false);

    assertThat(controller.isPermitted(enode1, enode2)).isTrue();

    verifyNoInteractions(syncStatusNodePermissioningProvider);
  }

  @Test
  public void whenQuorumQip714GateIsActiveShouldDelegateToProviders() {
    this.controller =
        new NodePermissioningController(
            syncStatusNodePermissioningProviderOptional,
            Collections.emptyList(),
            Optional.of(goQuorumQip714Gate));

    when(goQuorumQip714Gate.shouldCheckPermissions()).thenReturn(true);

    controller.isPermitted(enode1, enode2);

    verify(syncStatusNodePermissioningProvider, atLeast(1))
        .isConnectionPermitted(eq(enode1), eq(enode2));
  }
}
