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

import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.SnapDataRequest;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.request.TrieNodeDataRequest;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.services.tasks.Task;

public class CompleteTaskStep {
  private final SnapSyncState snapSyncState;
  private final Counter completedRequestsCounter;
  private final Counter retriedRequestsCounter;

  public CompleteTaskStep(final SnapSyncState snapSyncState, final MetricsSystem metricsSystem) {
    this.snapSyncState = snapSyncState;
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

  public synchronized void markAsCompleteOrFailed(
      final SnapWorldDownloadState downloadState, final Task<SnapDataRequest> task) {
    if (task.getData().isResponseReceived()
        || (task.getData() instanceof TrieNodeDataRequest
            && task.getData().isExpired(snapSyncState))) {
      completedRequestsCounter.inc();
      task.markCompleted();
      downloadState.checkCompletion(snapSyncState.getPivotBlockHeader().orElseThrow());
    } else {
      retriedRequestsCounter.inc();
      task.markFailed();
    }
    downloadState.notifyTaskAvailable();
  }
}
