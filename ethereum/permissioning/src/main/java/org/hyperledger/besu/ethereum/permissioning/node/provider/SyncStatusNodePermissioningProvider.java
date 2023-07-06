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
package org.hyperledger.besu.ethereum.permissioning.node.provider;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SyncStatusNodePermissioningProvider implements NodeConnectionPermissioningProvider {

  private final Synchronizer synchronizer;
  private final Set<URI> fixedNodes;
  private final Counter checkCounter;
  private final Counter checkCounterPermitted;
  private final Counter checkCounterUnpermitted;
  private final long inSyncSubscriberId;
  private final AtomicBoolean hasReachedSync = new AtomicBoolean(false);

  public SyncStatusNodePermissioningProvider(
      final Synchronizer synchronizer,
      final Collection<EnodeURL> fixedNodes,
      final MetricsSystem metricsSystem) {
    checkNotNull(synchronizer);
    this.synchronizer = synchronizer;
    this.inSyncSubscriberId = this.synchronizer.subscribeInSync(this::handleInSyncEvent, 0);
    this.fixedNodes =
        fixedNodes.stream().map(EnodeURL::toURIWithoutDiscoveryPort).collect(Collectors.toSet());

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.PERMISSIONING,
        "sync_status_node_sync_reached",
        "Whether the sync status permissioning provider has realised sync yet",
        () -> hasReachedSync.get() ? 1 : 0);
    this.checkCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "sync_status_node_check_count",
            "Number of times the sync status permissioning provider has been checked");
    this.checkCounterPermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "sync_status_node_check_count_permitted",
            "Number of times the sync status permissioning provider has been checked and returned permitted");
    this.checkCounterUnpermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "sync_status_node_check_count_unpermitted",
            "Number of times the sync status permissioning provider has been checked and returned unpermitted");
  }

  private void handleInSyncEvent(final boolean isInSync) {
    if (isInSync) {
      if (hasReachedSync.compareAndSet(false, true)) {
        synchronizer.unsubscribeInSync(inSyncSubscriberId);
      }
    }
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
  public boolean isConnectionPermitted(
      final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    if (hasReachedSync.get()) {
      return true;
    } else {
      checkCounter.inc();
      if (fixedNodes.contains(destinationEnode.toURIWithoutDiscoveryPort())) {
        checkCounterPermitted.inc();
        return true;
      } else {
        checkCounterUnpermitted.inc();
        return false;
      }
    }
  }

  public boolean hasReachedSync() {
    return hasReachedSync.get();
  }
}
