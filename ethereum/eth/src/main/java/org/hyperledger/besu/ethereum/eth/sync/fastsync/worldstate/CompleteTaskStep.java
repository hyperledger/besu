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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.services.tasks.Task;

import java.util.function.LongSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompleteTaskStep {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DISPLAY_PROGRESS_STEP = 100000;
  private final WorldStateStorage worldStateStorage;
  private final RunnableCounter completedRequestsCounter;
  private final Counter retriedRequestsCounter;
  private final LongSupplier worldStatePendingRequestsCurrentSupplier;

  public CompleteTaskStep(
      final WorldStateStorage worldStateStorage,
      final MetricsSystem metricsSystem,
      final LongSupplier worldStatePendingRequestsCurrentSupplier) {
    this.worldStateStorage = worldStateStorage;
    this.worldStatePendingRequestsCurrentSupplier = worldStatePendingRequestsCurrentSupplier;
    completedRequestsCounter =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "world_state_completed_requests_total",
                "Total number of node data requests completed as part of fast sync world state download"),
            this::displayWorldStateSyncProgress,
            DISPLAY_PROGRESS_STEP);
    retriedRequestsCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "world_state_retried_requests_total",
            "Total number of node data requests repeated as part of fast sync world state download");
  }

  public void markAsCompleteOrFailed(
      final BlockHeader header,
      final FastWorldDownloadState downloadState,
      final Task<NodeDataRequest> task) {
    if (task.getData().getData() != null) {
      enqueueChildren(task, header, downloadState);
      completedRequestsCounter.inc();
      task.markCompleted();
      downloadState.checkCompletion(worldStateStorage, header);
    } else {
      retriedRequestsCounter.inc();
      task.markFailed();
      // Marking the task as failed will add it back to the queue so make sure any threads
      // waiting to read from the queue are notified.
      downloadState.notifyTaskAvailable();
    }
  }

  private void displayWorldStateSyncProgress() {
    LOG.info(
        "Downloaded {} world state nodes. At least {} nodes remaining.",
        getCompletedRequests(),
        worldStatePendingRequestsCurrentSupplier.getAsLong());
  }

  long getCompletedRequests() {
    return completedRequestsCounter.get();
  }

  long getPendingRequests() {
    return worldStatePendingRequestsCurrentSupplier.getAsLong();
  }

  private void enqueueChildren(
      final Task<NodeDataRequest> task,
      final BlockHeader blockHeader,
      final FastWorldDownloadState downloadState) {
    final NodeDataRequest request = task.getData();
    // Only queue rootnode children if we started from scratch
    if (!downloadState.downloadWasResumed() || !isRootState(blockHeader, request)) {
      downloadState.enqueueRequests(request.getChildRequests(worldStateStorage));
    }
  }

  private boolean isRootState(final BlockHeader blockHeader, final NodeDataRequest request) {
    return request.getHash().equals(blockHeader.getStateRoot());
  }
}
