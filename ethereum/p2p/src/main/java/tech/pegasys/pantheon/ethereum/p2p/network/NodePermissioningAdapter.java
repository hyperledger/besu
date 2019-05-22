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
package tech.pegasys.pantheon.ethereum.p2p.network;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissions.PeerPermissions;
import tech.pegasys.pantheon.ethereum.permissioning.node.NodePermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class NodePermissioningAdapter extends PeerPermissions {
  private static final Logger LOG = LogManager.getLogger();

  private final NodePermissioningController nodePermissioningController;
  private final List<EnodeURL> bootnodes;
  private final Blockchain blockchain;
  private final long blockchainListenId;
  private final long nodePermissioningListenId;

  public NodePermissioningAdapter(
      final NodePermissioningController nodePermissioningController,
      final List<EnodeURL> bootnodes,
      final Blockchain blockchain) {
    checkNotNull(nodePermissioningController);
    checkNotNull(bootnodes);
    checkNotNull(blockchain);

    this.nodePermissioningController = nodePermissioningController;
    this.bootnodes = bootnodes;
    this.blockchain = blockchain;

    // TODO: These events should be more targeted
    blockchainListenId =
        blockchain.observeBlockAdded((evt, chain) -> dispatchUpdate(true, Optional.empty()));
    nodePermissioningListenId =
        this.nodePermissioningController.subscribeToUpdates(
            () -> dispatchUpdate(true, Optional.empty()));
  }

  @Override
  public boolean isPermitted(final Peer localNode, final Peer remotePeer, final Action action) {
    switch (action) {
      case DISCOVERY_ALLOW_IN_PEER_TABLE:
        return outboundIsPermitted(localNode, remotePeer);
      case DISCOVERY_ALLOW_OUTBOUND_NEIGHBORS_REQUEST:
        return allowOutboundNeighborsRequests(localNode, remotePeer);
      case DISCOVERY_ALLOW_OUTBOUND_BONDING:
        return allowOutboundBonding(localNode, remotePeer);
      case DISCOVERY_ACCEPT_INBOUND_BONDING:
      case DISCOVERY_SERVE_INBOUND_NEIGHBORS_REQUEST:
      case RLPX_ALLOW_NEW_INBOUND_CONNECTION:
        return inboundIsPermitted(localNode, remotePeer);
      case RLPX_ALLOW_NEW_OUTBOUND_CONNECTION:
        return outboundIsPermitted(localNode, remotePeer);
      case RLPX_ALLOW_ONGOING_CONNECTION:
        // TODO: This should probably check outbound || inbound, or the check should be more
        // granular
        return outboundIsPermitted(localNode, remotePeer);
      default:
        // Return false for unknown / unhandled permissions
        LOG.error(
            "Permissions denied for unknown action {}",
            action.name(),
            new IllegalStateException("Unhandled permissions action " + action.name()));
        return false;
    }
  }

  private boolean allowOutboundBonding(final Peer localNode, final Peer remotePeer) {
    boolean outboundMessagingAllowed = outboundIsPermitted(localNode, remotePeer);
    if (!nodePermissioningController.getSyncStatusNodePermissioningProvider().isPresent()) {
      return outboundMessagingAllowed;
    }

    // We're using smart-contract based permissioning
    // If we're out of sync, only allow bonding to our bootnodes
    final SyncStatusNodePermissioningProvider syncStatus =
        nodePermissioningController.getSyncStatusNodePermissioningProvider().get();
    return outboundMessagingAllowed
        && (syncStatus.hasReachedSync() || bootnodes.contains(remotePeer.getEnodeURL()));
  }

  private boolean allowOutboundNeighborsRequests(final Peer localNode, final Peer remotePeer) {
    boolean outboundMessagingAllowed = outboundIsPermitted(localNode, remotePeer);
    if (!nodePermissioningController.getSyncStatusNodePermissioningProvider().isPresent()) {
      return outboundMessagingAllowed;
    }

    // We're using smart-contract based permissioning
    // Only allow neighbors requests if we're in sync
    final SyncStatusNodePermissioningProvider syncStatus =
        nodePermissioningController.getSyncStatusNodePermissioningProvider().get();
    return outboundMessagingAllowed && syncStatus.hasReachedSync();
  }

  private boolean outboundIsPermitted(final Peer localNode, final Peer remotePeer) {
    return nodePermissioningController.isPermitted(
        localNode.getEnodeURL(), remotePeer.getEnodeURL());
  }

  private boolean inboundIsPermitted(final Peer localNode, final Peer remotePeer) {
    return nodePermissioningController.isPermitted(
        remotePeer.getEnodeURL(), localNode.getEnodeURL());
  }

  @Override
  public void close() {
    blockchain.removeObserver(blockchainListenId);
    nodePermissioningController.unsubscribeFromUpdates(nodePermissioningListenId);
  }
}
