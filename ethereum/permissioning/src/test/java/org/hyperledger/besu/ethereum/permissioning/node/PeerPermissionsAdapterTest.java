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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;
import org.hyperledger.besu.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.mockito.ArgumentMatchers;

public class PeerPermissionsAdapterTest {

  private final Peer localNode = createPeer();
  private final Peer remoteNode = createPeer();
  private final NodePermissioningController nodePermissioningController =
      mock(NodePermissioningController.class);
  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final MutableBlockchain blockchain =
      InMemoryKeyValueStorageProvider.createInMemoryBlockchain(gen.genesisBlock());
  private final List<EnodeURL> bootNodes = new ArrayList<>();
  private final PeerPermissionsAdapter adapter =
      new PeerPermissionsAdapter(nodePermissioningController, bootNodes, blockchain);

  @Test
  public void allowInPeerTable() {
    final Action action = Action.DISCOVERY_ALLOW_IN_PEER_TABLE;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();
  }

  @Test
  public void allowOutboundBonding_inSyncRemoteIsBootnode() {
    mockSyncStatusNodePermissioning(true, true);
    bootNodes.add(remoteNode.getEnodeURL());

    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_BONDING;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundBonding_inSyncRemoteIsNotABootnode() {
    mockSyncStatusNodePermissioning(true, true);

    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_BONDING;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  // This test smart-contract backed permissioning where while we’re syncing,
  // we can only trust bootnodes
  public void allowOutboundBonding_outOfSyncRemoteIsNotABootnode() {
    mockSyncStatusNodePermissioning(true, false);

    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_BONDING;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundBonding_outOfSyncRemoteIsABootnode() {
    mockSyncStatusNodePermissioning(true, false);
    bootNodes.add(remoteNode.getEnodeURL());

    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_BONDING;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundBonding_noSyncPermissioning() {
    mockSyncStatusNodePermissioning(false, false);
    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_BONDING;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowInboundBonding() {
    final Action action = Action.DISCOVERY_ACCEPT_INBOUND_BONDING;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundNeighborsRequest_inSyncRemoteIsBootnode() {
    mockSyncStatusNodePermissioning(true, true);
    bootNodes.add(remoteNode.getEnodeURL());
    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundNeighborsRequest_inSyncRemoteIsNotABootnode() {
    mockSyncStatusNodePermissioning(true, true);
    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundNeighborsRequest_outOfSyncRemoteIsABootnode() {
    mockSyncStatusNodePermissioning(true, false);
    bootNodes.add(remoteNode.getEnodeURL());
    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundNeighborsRequest_outOfSyncRemoteIsNotABootnode() {
    mockSyncStatusNodePermissioning(true, false);
    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOutboundNeighborsRequest_noSyncPermissioning() {
    mockSyncStatusNodePermissioning(false, false);
    final Action action = Action.DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowInboundNeighborsRequest() {
    final Action action = Action.DISCOVERY_SERVE_INBOUND_NEIGHBORS_REQUEST;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowLocallyInitiatedConnection() {
    final Action action = Action.RLPX_ALLOW_NEW_OUTBOUND_CONNECTION;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowRemotelyInitiatedConnection() {
    final Action action = Action.RLPX_ALLOW_NEW_INBOUND_CONNECTION;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOngoingLocallyInitiatedConnection() {
    final Action action = Action.RLPX_ALLOW_ONGOING_LOCALLY_INITIATED_CONNECTION;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void allowOngoingRemotelyInitiatedConnection() {
    final Action action = Action.RLPX_ALLOW_ONGOING_REMOTELY_INITIATED_CONNECTION;

    mockControllerPermissions(true, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();

    mockControllerPermissions(false, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(true, true);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isTrue();

    mockControllerPermissions(false, false);
    assertThat(adapter.isPermitted(localNode, remoteNode, action)).isFalse();
  }

  @Test
  public void subscribeUpdate_firesWhenBlockAdded() {
    final AtomicBoolean updateDispatched = new AtomicBoolean(false);
    adapter.subscribeUpdate((restricted, peers) -> updateDispatched.set(true));

    final Block newBlock = gen.nextBlock(blockchain.getGenesisBlock());
    blockchain.appendBlock(newBlock, gen.receipts(newBlock));

    assertThat(updateDispatched).isTrue();
  }

  private void mockSyncStatusNodePermissioning(final boolean isPresent, final boolean isInSync) {
    if (!isPresent) {
      when(nodePermissioningController.getSyncStatusNodePermissioningProvider())
          .thenReturn(Optional.empty());
      return;
    }

    final SyncStatusNodePermissioningProvider syncStatus =
        mock(SyncStatusNodePermissioningProvider.class);
    when(syncStatus.hasReachedSync()).thenReturn(isInSync);
    when(nodePermissioningController.getSyncStatusNodePermissioningProvider())
        .thenReturn(Optional.of(syncStatus));
  }

  private void mockControllerPermissions(
      final boolean allowLocalToRemote, final boolean allowRemoteToLocal) {
    when(nodePermissioningController.isPermitted(
            ArgumentMatchers.eq(localNode.getEnodeURL()),
            ArgumentMatchers.eq(remoteNode.getEnodeURL())))
        .thenReturn(allowLocalToRemote);
    when(nodePermissioningController.isPermitted(
            ArgumentMatchers.eq(remoteNode.getEnodeURL()),
            ArgumentMatchers.eq(localNode.getEnodeURL())))
        .thenReturn(allowRemoteToLocal);
  }

  private Peer createPeer() {
    return DefaultPeer.fromEnodeURL(
        EnodeURLImpl.builder()
            .ipAddress("127.0.0.1")
            .nodeId(Peer.randomId())
            .useDefaultPorts()
            .build());
  }
}
