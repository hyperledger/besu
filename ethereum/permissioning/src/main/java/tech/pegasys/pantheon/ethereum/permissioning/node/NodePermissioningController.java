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
package tech.pegasys.pantheon.ethereum.permissioning.node;

import tech.pegasys.pantheon.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.enode.EnodeURL;

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
  private final Subscribers<Runnable> permissioningUpdateSubscribers = new Subscribers<>();

  public NodePermissioningController(
      final Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProvider,
      final List<NodePermissioningProvider> providers) {
    this.providers = providers;
    this.syncStatusNodePermissioningProvider = syncStatusNodePermissioningProvider;
  }

  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    LOG.trace("Checking node permission: {} -> {}", sourceEnode, destinationEnode);

    if (syncStatusNodePermissioningProvider
        .map(p -> !p.hasReachedSync() && p.isPermitted(sourceEnode, destinationEnode))
        .orElse(false)) {
      return true;
    }

    final Optional<Boolean> insufficientPeerPermission =
        insufficientPeersPermissioningProvider.flatMap(
            p -> p.isPermitted(sourceEnode, destinationEnode));

    if (insufficientPeerPermission.isPresent()) {
      return insufficientPeerPermission.get();
    }

    if (syncStatusNodePermissioningProvider.isPresent()
        && !syncStatusNodePermissioningProvider.get().isPermitted(sourceEnode, destinationEnode)) {
      return false;
    } else {
      for (final NodePermissioningProvider provider : providers) {
        if (!provider.isPermitted(sourceEnode, destinationEnode)) {
          return false;
        }
      }
    }
    return true;
  }

  public void startPeerDiscoveryCallback(final Runnable peerDiscoveryCallback) {
    if (syncStatusNodePermissioningProvider.isPresent()) {
      syncStatusNodePermissioningProvider.get().setHasReachedSyncCallback(peerDiscoveryCallback);
    } else {
      peerDiscoveryCallback.run();
    }
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
}
