/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.services.tasks.Task;

import java.util.function.LongSupplier;

public class CompleteTaskStep {
  private final WorldStateStorage worldStateStorage;
  private final Counter completedRequestsCounter;
  private final Counter retriedRequestsCounter;
  private final LongSupplier worldStatePendingRequestsCurrentSupplier;

  public CompleteTaskStep(
      final WorldStateStorage worldStateStorage,
      final MetricsSystem metricsSystem,
      final LongSupplier worldStatePendingRequestsCurrentSupplier) {
    this.worldStateStorage = worldStateStorage;
    this.worldStatePendingRequestsCurrentSupplier = worldStatePendingRequestsCurrentSupplier;
    completedRequestsCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_completed_requests_total",
            "Total number of node data requests completed as part of snap sync world state download");
    retriedRequestsCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "snap_world_state_retried_requests_total",
            "Total number of node data requests repeated as part of snap sync world state download");
  }

  public void markAsCompleteOrFailed(
      final SnapSyncState snapSyncState,
      final SnapWorldDownloadState downloadState,
      final Task<SnapDataRequest> task) {
    if (task.getData().getData().isPresent()) {
      enqueueChildren(task, downloadState);
      completedRequestsCounter.inc();
      task.markCompleted();
      downloadState.checkCompletion(
          worldStateStorage, snapSyncState.getPivotBlockHeader().orElseThrow());
    } else {
      retriedRequestsCounter.inc();
      task.markFailed();
      // Marking the task as failed will add it back to the queue so make sure any threads
      // waiting to read from the queue are notified.
      downloadState.notifyTaskAvailable();
    }
  }

  public long getPendingRequests() {
    return worldStatePendingRequestsCurrentSupplier.getAsLong();
  }

  private void enqueueChildren(
      final Task<SnapDataRequest> task, final SnapWorldDownloadState downloadState) {
    final SnapDataRequest request = task.getData();
    // Only queue rootnode children if we started from scratch
    if (!downloadState.downloadWasResumed()) {
      downloadState.enqueueRequests(request.getChildRequests(worldStateStorage));
    }
  }
}
