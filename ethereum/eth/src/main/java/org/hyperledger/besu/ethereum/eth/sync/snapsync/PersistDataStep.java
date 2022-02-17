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
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.Task;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistDataStep {

  private static final Logger LOG = LoggerFactory.getLogger(PersistDataStep.class);

  private static final int DISPLAY_PROGRESS_STEP = 100000;

  private final SnapSyncState snapSyncState;

  private final WorldStateStorage worldStateStorage;

  private final RunnableCounter generatedNodes;

  private final RunnableCounter healedNodes;

  public PersistDataStep(
      final SnapSyncState snapSyncState,
      final WorldStateStorage worldStateStorage,
      final MetricsSystem metricsSystem) {
    this.snapSyncState = snapSyncState;
    this.worldStateStorage = worldStateStorage;

    this.generatedNodes =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "snapsync_world_state_generated_nodes_total",
                "Total number of data nodes generated as part of snap sync world state download"),
            this::displayWorldStateSyncProgress,
            DISPLAY_PROGRESS_STEP);
    this.healedNodes =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "snapsync_world_state_healed_nodes_total",
                "Total number of data nodes healed as part of snap sync world state heal process"),
            this::displayHealProgress,
            DISPLAY_PROGRESS_STEP);
  }

  public List<Task<SnapDataRequest>> persist(
      final List<Task<SnapDataRequest>> tasks,
      final WorldDownloadState<SnapDataRequest> downloadState) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    for (Task<SnapDataRequest> task : tasks) {
      if (task.getData().isDataPresent()) {
        enqueueChildren(task, downloadState);
        final int persistedNodes = task.getData().persist(worldStateStorage, updater);
        if (persistedNodes > 0) {
          if (snapSyncState.isHealInProgress()) {
            healedNodes.inc(persistedNodes);
          } else {
            generatedNodes.inc(persistedNodes);
          }
        }
      }
    }
    updater.commit();
    return tasks;
  }

  public Task<SnapDataRequest> persist(
      final Task<SnapDataRequest> task, final WorldDownloadState<SnapDataRequest> downloadState) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    if (task.getData().isDataPresent()) {
      enqueueChildren(task, downloadState);
      final int persistedNodes = task.getData().persist(worldStateStorage, updater);
      if (persistedNodes > 0) {
        if (snapSyncState.isHealInProgress()) {
          healedNodes.inc(persistedNodes);
        } else {
          generatedNodes.inc(persistedNodes);
        }
      }
    }
    updater.commit();
    return task;
  }

  private void enqueueChildren(
      final Task<SnapDataRequest> task, final WorldDownloadState<SnapDataRequest> downloadState) {
    final SnapDataRequest request = task.getData();
    // Only queue rootnode children if we started from scratch
    if (!downloadState.downloadWasResumed()) {
      downloadState.enqueueRequests(request.getChildRequests(worldStateStorage));
    }
  }

  private void displayWorldStateSyncProgress() {
    LOG.info("Generated {} world state nodes", generatedNodes.get());
  }

  private void displayHealProgress() {
    LOG.info("Healed {} world sync nodes", healedNodes.get());
  }
}
