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
package org.hyperledger.besu.ethereum.permissioning.node;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodePermissioningController {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProvider;
  private Optional<ContextualNodePermissioningProvider> insufficientPeersPermissioningProvider =
      Optional.empty();
  private final List<NodePermissioningProvider> providers;
  private final Subscribers<Runnable> permissioningUpdateSubscribers = Subscribers.create();

  public NodePermissioningController(
      final Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProvider,
      final List<NodePermissioningProvider> providers) {
    this.providers = providers;
    this.syncStatusNodePermissioningProvider = syncStatusNodePermissioningProvider;
  }

  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    LOG.trace("Node permissioning: Checking {} -> {}", sourceEnode, destinationEnode);

    if (syncStatusNodePermissioningProvider
        .map(p -> !p.hasReachedSync() && p.isPermitted(sourceEnode, destinationEnode))
        .orElse(false)) {

      LOG.trace(
          "Node permissioning - Sync Status: Permitted {} -> {}", sourceEnode, destinationEnode);

      return true;
    }

    final Optional<Boolean> insufficientPeerPermission =
        insufficientPeersPermissioningProvider.flatMap(
            p -> p.isPermitted(sourceEnode, destinationEnode));

    if (insufficientPeerPermission.isPresent()) {
      final Boolean permitted = insufficientPeerPermission.get();

      LOG.trace(
          "Node permissioning - Insufficient Peers: {} {} -> {}",
          permitted ? "Permitted" : "Rejected",
          sourceEnode,
          destinationEnode);

      return permitted;
    }

    if (syncStatusNodePermissioningProvider.isPresent()
        && !syncStatusNodePermissioningProvider.get().isPermitted(sourceEnode, destinationEnode)) {

      LOG.trace(
          "Node permissioning - Sync Status: Rejected {} -> {}", sourceEnode, destinationEnode);

      return false;
    } else {
      for (final NodePermissioningProvider provider : providers) {
        if (!provider.isPermitted(sourceEnode, destinationEnode)) {
          LOG.trace(
              "Node permissioning - {}: Rejected {} -> {}",
              provider.getClass().getSimpleName(),
              sourceEnode,
              destinationEnode);

          return false;
        }
      }
    }

    LOG.trace("Node permissioning: Permitted {} -> {}", sourceEnode, destinationEnode);

    return true;
  }

  public void setInsufficientPeersPermissioningProvider(
      final ContextualNodePermissioningProvider insufficientPeersPermissioningProvider) {
    insufficientPeersPermissioningProvider.subscribeToUpdates(
        () -> permissioningUpdateSubscribers.forEach(Runnable::run));
    this.insufficientPeersPermissioningProvider =
        Optional.of(insufficientPeersPermissioningProvider);
  }

  public Optional<SyncStatusNodePermissioningProvider> getSyncStatusNodePermissioningProvider() {
    return syncStatusNodePermissioningProvider;
  }

  public List<NodePermissioningProvider> getProviders() {
    return providers;
  }

  public long subscribeToUpdates(final Runnable callback) {
    return permissioningUpdateSubscribers.subscribe(callback);
  }

  public boolean unsubscribeFromUpdates(final long id) {
    return permissioningUpdateSubscribers.unsubscribe(id);
  }

  public Optional<NodeLocalConfigPermissioningController> localConfigController() {
    return getProviders().stream()
        .filter(p -> p instanceof NodeLocalConfigPermissioningController)
        .findFirst()
        .map(n -> (NodeLocalConfigPermissioningController) n);
  }
}
