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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.metrics.RunnableCounter;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.services.tasks.Task;

import java.text.DecimalFormat;
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
  private final DecimalFormat doubleFormatter = new DecimalFormat("#.##");
  private double estimatedWorldStateCompletion;

  public CompleteTaskStep(
      final WorldStateStorage worldStateStorage,
      final MetricsSystem metricsSystem,
      final LongSupplier worldStatePendingRequestsCurrentSupplier) {
    this.worldStateStorage = worldStateStorage;
    this.worldStatePendingRequestsCurrentSupplier = worldStatePendingRequestsCurrentSupplier;
    completedRequestsCounter =
        new RunnableCounter(
            metricsSystem.createCounter(
                PantheonMetricCategory.SYNCHRONIZER,
                "world_state_completed_requests_total",
                "Total number of node data requests completed as part of fast sync world state download"),
            this::displayWorldStateSyncProgress,
            DISPLAY_PROGRESS_STEP);
    retriedRequestsCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.SYNCHRONIZER,
            "world_state_retried_requests_total",
            "Total number of node data requests repeated as part of fast sync world state download");
  }

  public void markAsCompleteOrFailed(
      final BlockHeader header,
      final WorldDownloadState downloadState,
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
        "Downloaded {} world state nodes. At least {} nodes remaining. Estimated World State completion: {} %.",
        completedRequestsCounter.get(),
        worldStatePendingRequestsCurrentSupplier.getAsLong(),
        doubleFormatter.format(computeWorldStateSyncProgress() * 100.0));
  }

  public double computeWorldStateSyncProgress() {
    final double pendingRequests = worldStatePendingRequestsCurrentSupplier.getAsLong();
    final double completedRequests = completedRequestsCounter.get();
    estimatedWorldStateCompletion = completedRequests / (completedRequests + pendingRequests);
    return this.estimatedWorldStateCompletion;
  }

  private void enqueueChildren(
      final Task<NodeDataRequest> task,
      final BlockHeader blockHeader,
      final WorldDownloadState downloadState) {
    final NodeDataRequest request = task.getData();
    // Only queue rootnode children if we started from scratch
    if (!downloadState.downloadWasResumed() || !isRootState(blockHeader, request)) {
      downloadState.enqueueRequests(request.getChildRequests());
    }
  }

  private boolean isRootState(final BlockHeader blockHeader, final NodeDataRequest request) {
    return request.getHash().equals(blockHeader.getStateRoot());
  }
}
