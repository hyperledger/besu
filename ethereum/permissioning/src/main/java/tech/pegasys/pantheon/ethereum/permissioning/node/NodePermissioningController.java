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
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodePermissioningController {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<SyncStatusNodePermissioningProvider> syncStatusNodePermissioningProvider;

  public NodePermissioningController(
      final SyncStatusNodePermissioningProvider syncStatusNodePermissioningProvider) {
    this.syncStatusNodePermissioningProvider = Optional.of(syncStatusNodePermissioningProvider);
  }

  public NodePermissioningController() {
    this.syncStatusNodePermissioningProvider = Optional.empty();
  }

  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    LOG.trace("Checking node permission: {} -> {}", sourceEnode, destinationEnode);

    return syncStatusNodePermissioningProvider
        .map((provider) -> provider.isPermitted(sourceEnode, destinationEnode))
        .orElse(true);
  }

  public void startPeerDiscoveryCallback(final Runnable peerDiscoveryCallback) {
    syncStatusNodePermissioningProvider.ifPresent(
        (p) -> p.setHasReachedSyncCallback(peerDiscoveryCallback));
  }
}
