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

import org.hyperledger.besu.ethereum.permissioning.GoQuorumQip714Gate;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodePermissioningController {

  private static final Logger LOG = LoggerFactory.getLogger(NodePermissioningController.class);

  private final Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProvider;
  private Optional<ContextualNodePermissioningProvider> insufficientPeersPermissioningProvider =
      Optional.empty();
  private final List<NodeConnectionPermissioningProvider> providers;
  private final Optional<GoQuorumQip714Gate> goQuorumQip714Gate;
  private final Subscribers<Runnable> permissioningUpdateSubscribers = Subscribers.create();

  public NodePermissioningController(
      final Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProvider,
      final List<NodeConnectionPermissioningProvider> providers,
      final Optional<GoQuorumQip714Gate> goQuorumQip714Gate) {
    this.providers = providers;
    this.syncStatusNodePermissioningProvider = syncStatusNodePermissioningProvider;
    this.goQuorumQip714Gate = goQuorumQip714Gate;
  }

  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    final boolean checkPermissions =
        goQuorumQip714Gate.map(GoQuorumQip714Gate::shouldCheckPermissions).orElse(true);
    if (!checkPermissions) {
      LOG.trace("Skipping node permissioning check due to qip714block config");

      return true;
    }

    LOG.trace("Node permissioning: Checking {} -> {}", sourceEnode, destinationEnode);

    if (syncStatusNodePermissioningProvider
        .map(p -> !p.hasReachedSync() && p.isConnectionPermitted(sourceEnode, destinationEnode))
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
        && !syncStatusNodePermissioningProvider
            .get()
            .isConnectionPermitted(sourceEnode, destinationEnode)) {

      LOG.trace(
          "Node permissioning - Sync Status: Rejected {} -> {}", sourceEnode, destinationEnode);

      return false;
    } else {
      for (final NodeConnectionPermissioningProvider provider : providers) {
        if (!provider.isConnectionPermitted(sourceEnode, destinationEnode)) {
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

  public List<NodeConnectionPermissioningProvider> getProviders() {
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
