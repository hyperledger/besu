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
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.tasks.Task;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PersistDataStep {

  private static final Logger LOG = LogManager.getLogger();

  private static final int DISPLAY_PROGRESS_STEP = 100_000;

  private final WorldStateStorage worldStateStorage;

  private RunnableCounter completedNodes;

  public PersistDataStep(
      final WorldStateStorage worldStateStorage, final MetricsSystem metricsSystem) {
    this.worldStateStorage = worldStateStorage;

    this.completedNodes =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.SYNCHRONIZER,
                "snapsync_world_state_completed_nodes_total",
                "Total number of node data completed as part of snap sync world state download"),
            this::displayWorldStateSyncProgress,
            DISPLAY_PROGRESS_STEP);
  }

  public Task<SnapDataRequest> persist(
      final Task<SnapDataRequest> task, final HealNodeCollection healNodeCollection) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    if (task.getData().getData().isPresent()) {
      final int persistedNodes =
          task.getData().persist(worldStateStorage, updater, healNodeCollection);
      completedNodes.inc(persistedNodes);
    }
    updater.commit();
    return task;
  }

  private void displayWorldStateSyncProgress() {
    LOG.warn("Snapsync is an experimental feature you can use it at your own risk");
    LOG.info("Generated {} world state nodes from snapsync", completedNodes.get());
  }
}
