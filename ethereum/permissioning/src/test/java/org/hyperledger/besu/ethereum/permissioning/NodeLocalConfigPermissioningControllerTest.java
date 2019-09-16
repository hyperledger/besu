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
package org.hyperledger.besu.ethereum.permissioning;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.node.NodeWhitelistUpdatedEvent;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class NodeLocalConfigPermissioningControllerTest {

  @Mock private WhitelistPersistor whitelistPersistor;
  private final List<EnodeURL> bootnodesList = new ArrayList<>();
  private NodeLocalConfigPermissioningController controller;

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode2 =
      "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final EnodeURL selfEnode =
      EnodeURL.fromString(
          "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:1111");
  @Mock private MetricsSystem metricsSystem;
  @Mock private Counter checkCounter;
  @Mock private Counter checkPermittedCounter;
  @Mock private Counter checkUnpermittedCounter;

  @Before
  public void setUp() {
    bootnodesList.clear();

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_local_check_count",
            "Number of times the node local permissioning provider has been checked"))
        .thenReturn(checkCounter);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_local_check_count_permitted",
            "Number of times the node local permissioning provider has been checked and returned permitted"))
        .thenReturn(checkPermittedCounter);

    when(metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_local_check_count_unpermitted",
            "Number of times the node local permissioning provider has been checked and returned unpermitted"))
        .thenReturn(checkUnpermittedCounter);

    controller =
        new NodeLocalConfigPermissioningController(
            LocalPermissioningConfiguration.createDefault(),
            bootnodesList,
            selfEnode.getNodeId(),
            whitelistPersistor,
            metricsSystem);
  }

  @Test
  public void whenAddNodesWithValidInputShouldReturnSuccess() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.SUCCESS);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.addNodes(Lists.newArrayList(enode1));

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
    assertThat(controller.getNodesWhitelist()).containsExactly(enode1);
  }

  @Test
  public void whenAddNodesInputHasExistingNodeShouldReturnAddErrorExistingEntry() {
    controller.addNodes(Arrays.asList(enode1));

    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EXISTING_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.addNodes(Lists.newArrayList(enode1, enode2));

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenAddNodesInputHasDuplicatedNodesShouldReturnDuplicatedEntryError() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_DUPLICATED_ENTRY);

    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.addNodes(Arrays.asList(enode1, enode1));

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenAddNodesInputHasEmptyListOfNodesShouldReturnErrorEmptyEntry() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EMPTY_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(new ArrayList<>());

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenAddNodesInputHasNullListOfNodesShouldReturnErrorEmptyEntry() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EMPTY_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(null);

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenRemoveNodesInputHasAbsentNodeShouldReturnRemoveErrorAbsentEntry() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_ABSENT_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(Lists.newArrayList(enode1, enode2));

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenRemoveNodesInputHasDuplicateNodesShouldReturnErrorDuplicatedEntry() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_DUPLICATED_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(Lists.newArrayList(enode1, enode1));

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenRemoveNodesInputHasEmptyListOfNodesShouldReturnErrorEmptyEntry() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EMPTY_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(new ArrayList<>());

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenRemoveNodesInputHasNullListOfNodesShouldReturnErrorEmptyEntry() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EMPTY_ENTRY);
    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(null);

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
  }

  @Test
  public void whenNodeIdsAreDifferentItShouldNotBePermitted() {
    String peer1 =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303";
    String peer2 =
        "enode://bbbb80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303";

    controller.addNodes(Arrays.asList(peer1));
    assertThat(controller.isPermitted(peer2)).isFalse();

    verifyCountersUntouched();

    assertThat(controller.isPermitted(EnodeURL.fromString(peer2), EnodeURL.fromString(peer1)))
        .isFalse();

    verifyCountersUnpermitted();
  }

  @Test
  public void whenNodesHostsAreDifferentItShouldNotBePermitted() {
    String peer1 =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303";
    String peer2 =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.2:30303";

    controller.addNodes(Arrays.asList(peer1));

    assertThat(controller.isPermitted(peer2)).isFalse();
  }

  @Test
  public void whenNodesListeningPortsAreDifferentItShouldNotBePermitted() {
    String peer1 =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30301";
    String peer2 =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30302";

    controller.addNodes(Arrays.asList(peer1));

    assertThat(controller.isPermitted(peer2)).isFalse();
  }

  @Test
  public void
      whenCheckingIfNodeIsPermittedDiscoveryPortShouldNotBeConsidered_implicitDiscPortMismatch() {
    String peerWithDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=10001";
    String peerWithoutDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303";

    controller.addNodes(Arrays.asList(peerWithDiscoveryPortSet));

    assertThat(controller.isPermitted(peerWithoutDiscoveryPortSet)).isTrue();
  }

  @Test
  public void
      whenCheckingIfNodeIsPermittedDiscoveryPortShouldBeNotConsidered_explicitDiscPortMismatch() {
    String peerWithDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=10001";
    String peerWithDifferentDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=10002";

    controller.addNodes(Arrays.asList(peerWithDifferentDiscoveryPortSet));

    assertThat(controller.isPermitted(peerWithDiscoveryPortSet)).isTrue();
  }

  @Test
  public void
      whenCheckingIfNodeIsPermittedDiscoveryPortShouldNotBeConsidered_nodeToCheckHasDiscDisabled() {
    String peerWithDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=10001";
    String peerWithoutDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=0";

    controller.addNodes(Arrays.asList(peerWithDiscoveryPortSet));

    assertThat(controller.isPermitted(peerWithoutDiscoveryPortSet)).isTrue();
  }

  @Test
  public void
      whenCheckingIfNodeIsPermittedDiscoveryPortShouldNotBeConsidered_whitelistedNodeHasDiscDisabled() {
    String peerWithDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=0";
    String peerWithoutDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=123";

    controller.addNodes(Arrays.asList(peerWithDiscoveryPortSet));

    assertThat(controller.isPermitted(peerWithoutDiscoveryPortSet)).isTrue();
  }

  @Test
  public void
      whenCheckingIfNodeIsPermittedDiscoveryPortShouldNotBeConsidered_whitelistAndNodeHaveDiscDisabled() {
    String peerWithDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=0";
    String peerWithoutDiscoveryPortSet =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@127.0.0.1:30303?discport=0";

    controller.addNodes(Arrays.asList(peerWithDiscoveryPortSet));

    assertThat(controller.isPermitted(peerWithoutDiscoveryPortSet)).isTrue();
  }

  @Test
  public void whenCheckingIfNodeIsPermittedOrderDoesNotMatter() {
    controller.addNodes(Arrays.asList(enode1));

    verifyCountersUntouched();

    assertThat(controller.isPermitted(EnodeURL.fromString(enode1), selfEnode)).isTrue();

    verifyCountersPermitted();

    assertThat(controller.isPermitted(selfEnode, EnodeURL.fromString(enode1))).isTrue();
  }

  @Test
  public void stateShouldRevertIfWhitelistPersistFails()
      throws IOException, WhitelistFileSyncException {
    List<String> newNode1 = singletonList(EnodeURL.fromString(enode1).toString());
    List<String> newNode2 = singletonList(EnodeURL.fromString(enode2).toString());

    assertThat(controller.getNodesWhitelist().size()).isEqualTo(0);

    controller.addNodes(newNode1);
    assertThat(controller.getNodesWhitelist().size()).isEqualTo(1);

    doThrow(new IOException()).when(whitelistPersistor).updateConfig(any(), any());
    controller.addNodes(newNode2);

    assertThat(controller.getNodesWhitelist().size()).isEqualTo(1);
    assertThat(controller.getNodesWhitelist()).isEqualTo(newNode1);

    verify(whitelistPersistor, times(3)).verifyConfigFileMatchesState(any(), any());
    verify(whitelistPersistor, times(2)).updateConfig(any(), any());
    verifyNoMoreInteractions(whitelistPersistor);
  }

  @Test
  public void reloadNodeWhitelistWithValidConfigFileShouldUpdateWhitelist() throws Exception {
    final String expectedEnodeURL =
        "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.9:4567";
    final Path permissionsFile = createPermissionsFileWithNode(expectedEnodeURL);
    final LocalPermissioningConfiguration permissioningConfig =
        mock(LocalPermissioningConfiguration.class);

    when(permissioningConfig.getNodePermissioningConfigFilePath())
        .thenReturn(permissionsFile.toAbsolutePath().toString());
    when(permissioningConfig.isNodeWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getNodeWhitelist())
        .thenReturn(Arrays.asList(URI.create(expectedEnodeURL)));
    controller =
        new NodeLocalConfigPermissioningController(
            permissioningConfig, bootnodesList, selfEnode.getNodeId(), metricsSystem);

    controller.reload();

    assertThat(controller.getNodesWhitelist()).containsExactly(expectedEnodeURL);
  }

  @Test
  public void reloadNodeWhitelistWithErrorReadingConfigFileShouldKeepOldWhitelist() {
    final String expectedEnodeURI =
        "enode://aaaa80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.9:4567";
    final LocalPermissioningConfiguration permissioningConfig =
        mock(LocalPermissioningConfiguration.class);

    when(permissioningConfig.getNodePermissioningConfigFilePath()).thenReturn("foo");
    when(permissioningConfig.isNodeWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getNodeWhitelist())
        .thenReturn(Arrays.asList(URI.create(expectedEnodeURI)));
    controller =
        new NodeLocalConfigPermissioningController(
            permissioningConfig, bootnodesList, selfEnode.getNodeId(), metricsSystem);

    final Throwable thrown = catchThrowable(() -> controller.reload());

    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unable to read permissioning TOML config file");

    assertThat(controller.getNodesWhitelist()).containsExactly(expectedEnodeURI);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void whenAddingNodeShouldNotifyWhitelistModifiedSubscribers() {
    final Consumer<NodeWhitelistUpdatedEvent> consumer = mock(Consumer.class);
    final NodeWhitelistUpdatedEvent expectedEvent =
        new NodeWhitelistUpdatedEvent(
            Lists.newArrayList(EnodeURL.fromString(enode1)), Collections.emptyList());

    controller.subscribeToListUpdatedEvent(consumer);
    controller.addNodes(Lists.newArrayList(enode1));

    verify(consumer).accept(eq(expectedEvent));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void whenAddingNodeDoesNotAddShouldNotNotifyWhitelistModifiedSubscribers() {
    // adding node before subscribing to whitelist modified events
    controller.addNodes(Lists.newArrayList(enode1));
    final Consumer<NodeWhitelistUpdatedEvent> consumer = mock(Consumer.class);

    controller.subscribeToListUpdatedEvent(consumer);
    // won't add duplicate node
    controller.addNodes(Lists.newArrayList(enode1));

    verifyZeroInteractions(consumer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void whenRemovingNodeShouldNotifyWhitelistModifiedSubscribers() {
    // adding node before subscribing to whitelist modified events
    controller.addNodes(Lists.newArrayList(enode1));

    final Consumer<NodeWhitelistUpdatedEvent> consumer = mock(Consumer.class);
    final NodeWhitelistUpdatedEvent expectedEvent =
        new NodeWhitelistUpdatedEvent(
            Collections.emptyList(), Lists.newArrayList(EnodeURL.fromString(enode1)));

    controller.subscribeToListUpdatedEvent(consumer);
    controller.removeNodes(Lists.newArrayList(enode1));

    verify(consumer).accept(eq(expectedEvent));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void whenRemovingNodeDoesNotRemoveShouldNotifyWhitelistModifiedSubscribers() {
    final Consumer<NodeWhitelistUpdatedEvent> consumer = mock(Consumer.class);

    controller.subscribeToListUpdatedEvent(consumer);
    // won't remove absent node
    controller.removeNodes(Lists.newArrayList(enode1));

    verifyZeroInteractions(consumer);
  }

  @Test
  public void whenRemovingBootnodeShouldReturnRemoveBootnodeError() {
    NodeLocalConfigPermissioningController.NodesWhitelistResult expected =
        new NodeLocalConfigPermissioningController.NodesWhitelistResult(
            WhitelistOperationResult.ERROR_FIXED_NODE_CANNOT_BE_REMOVED);
    bootnodesList.add(EnodeURL.fromString(enode1));
    controller.addNodes(Lists.newArrayList(enode1, enode2));

    NodeLocalConfigPermissioningController.NodesWhitelistResult actualResult =
        controller.removeNodes(Lists.newArrayList(enode1));

    assertThat(actualResult).isEqualToComparingOnlyGivenFields(expected, "result");
    assertThat(controller.getNodesWhitelist()).containsExactly(enode1, enode2);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void whenReloadingWhitelistShouldNotifyWhitelistModifiedSubscribers() throws Exception {
    final Path permissionsFile = createPermissionsFileWithNode(enode2);
    final LocalPermissioningConfiguration permissioningConfig =
        mock(LocalPermissioningConfiguration.class);
    final Consumer<NodeWhitelistUpdatedEvent> consumer = mock(Consumer.class);
    final NodeWhitelistUpdatedEvent expectedEvent =
        new NodeWhitelistUpdatedEvent(
            Lists.newArrayList(EnodeURL.fromString(enode2)),
            Lists.newArrayList(EnodeURL.fromString(enode1)));

    when(permissioningConfig.getNodePermissioningConfigFilePath())
        .thenReturn(permissionsFile.toAbsolutePath().toString());
    when(permissioningConfig.isNodeWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getNodeWhitelist()).thenReturn(Arrays.asList(URI.create(enode1)));
    controller =
        new NodeLocalConfigPermissioningController(
            permissioningConfig, bootnodesList, selfEnode.getNodeId(), metricsSystem);
    controller.subscribeToListUpdatedEvent(consumer);

    controller.reload();

    verify(consumer).accept(eq(expectedEvent));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void whenReloadingWhitelistAndNothingChangesShouldNotNotifyWhitelistModifiedSubscribers()
      throws Exception {
    final Path permissionsFile = createPermissionsFileWithNode(enode1);
    final LocalPermissioningConfiguration permissioningConfig =
        mock(LocalPermissioningConfiguration.class);
    final Consumer<NodeWhitelistUpdatedEvent> consumer = mock(Consumer.class);

    when(permissioningConfig.getNodePermissioningConfigFilePath())
        .thenReturn(permissionsFile.toAbsolutePath().toString());
    when(permissioningConfig.isNodeWhitelistEnabled()).thenReturn(true);
    when(permissioningConfig.getNodeWhitelist()).thenReturn(Arrays.asList(URI.create(enode1)));
    controller =
        new NodeLocalConfigPermissioningController(
            permissioningConfig, bootnodesList, selfEnode.getNodeId(), metricsSystem);
    controller.subscribeToListUpdatedEvent(consumer);

    controller.reload();

    verifyZeroInteractions(consumer);
  }

  private Path createPermissionsFileWithNode(final String node) throws IOException {
    final String nodePermissionsFileContent = "nodes-whitelist=[\"" + node + "\"]";
    final Path permissionsFile = Files.createTempFile("node_permissions", "");
    permissionsFile.toFile().deleteOnExit();
    Files.write(permissionsFile, nodePermissionsFileContent.getBytes(StandardCharsets.UTF_8));
    return permissionsFile;
  }

  private void verifyCountersUntouched() {
    verify(checkCounter, times(0)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  private void verifyCountersPermitted() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(1)).inc();
    verify(checkUnpermittedCounter, times(0)).inc();
  }

  private void verifyCountersUnpermitted() {
    verify(checkCounter, times(1)).inc();
    verify(checkPermittedCounter, times(0)).inc();
    verify(checkUnpermittedCounter, times(1)).inc();
  }
}
