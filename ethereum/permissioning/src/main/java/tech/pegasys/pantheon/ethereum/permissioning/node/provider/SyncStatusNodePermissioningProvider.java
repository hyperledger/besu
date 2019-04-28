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

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.permissioning.node.NodePermissioningProvider;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;

public class SyncStatusNodePermissioningProvider implements NodePermissioningProvider {

  private final Synchronizer synchronizer;
  private final Collection<EnodeURL> fixedNodes = new HashSet<>();
  private OptionalLong syncStatusObserverId;
  private boolean hasReachedSync = false;
  private Optional<Runnable> hasReachedSyncCallback = Optional.empty();

  public SyncStatusNodePermissioningProvider(
      final Synchronizer synchronizer, final Collection<EnodeURL> fixedNodes) {
    checkNotNull(synchronizer);
    this.synchronizer = synchronizer;
    long id = this.synchronizer.observeSyncStatus(this::handleSyncStatusUpdate);
    this.syncStatusObserverId = OptionalLong.of(id);
    this.fixedNodes.addAll(fixedNodes);
  }

  private void handleSyncStatusUpdate(final SyncStatus syncStatus) {
    if (syncStatus != null) {
      long blocksBehind = syncStatus.getHighestBlock() - syncStatus.getCurrentBlock();
      if (blocksBehind <= 0) {
        synchronized (this) {
          if (!hasReachedSync) {
            runCallback();
            syncStatusObserverId.ifPresent(
                id -> {
                  synchronizer.removeObserver(id);
                  syncStatusObserverId = OptionalLong.empty();
                });
            hasReachedSync = true;
          }
        }
      }
    }
  }

  public synchronized void setHasReachedSyncCallback(final Runnable runnable) {
    if (hasReachedSync) {
      runCallback();
    } else {
      this.hasReachedSyncCallback = Optional.of(runnable);
    }
  }

  private synchronized void runCallback() {
    hasReachedSyncCallback.ifPresent(Runnable::run);
    hasReachedSyncCallback = Optional.empty();
  }

  /**
   * Before reaching a sync'd state, the node will only be allowed to talk to its fixedNodes
   * (outgoing connections). After reaching a sync'd state, it is expected that other providers will
   * check the permissions (most likely the smart contract based provider). That's why we always
   * return true after reaching a sync'd state.
   *
   * @param sourceEnode the enode source of the packet or connection
   * @param destinationEnode the enode target of the packet or connection
   * @return true, if the communication from sourceEnode to destinationEnode is permitted, false
   *     otherwise
   */
  @Override
  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    if (hasReachedSync) {
      return true;
    } else {
      return fixedNodes.contains(destinationEnode);
    }
  }

  public boolean hasReachedSync() {
    return hasReachedSync;
  }
}
