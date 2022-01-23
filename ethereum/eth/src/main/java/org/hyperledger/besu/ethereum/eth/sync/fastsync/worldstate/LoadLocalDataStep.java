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

import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.services.pipeline.Pipe;
import org.hyperledger.besu.services.tasks.Task;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public class LoadLocalDataStep {

  private final WorldStateStorage worldStateStorage;
  private final Counter existingNodeCounter;

  public LoadLocalDataStep(
      final WorldStateStorage worldStateStorage, final MetricsSystem metricsSystem) {
    this.worldStateStorage = worldStateStorage;
    existingNodeCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.SYNCHRONIZER,
            "world_state_existing_nodes_total",
            "Total number of node data requests completed using existing data");
  }

  public Stream<Task<NodeDataRequest>> loadLocalData(
      final Task<NodeDataRequest> task, final Pipe<Task<NodeDataRequest>> completedTasks) {
    final NodeDataRequest request = task.getData();
    final Optional<Bytes> existingData = request.getExistingData(worldStateStorage);
    if (existingData.isPresent()) {
      existingNodeCounter.inc();
      request.setData(existingData.get());
      request.setRequiresPersisting(false);
      final WorldStateStorage.Updater updater = worldStateStorage.updater();
      request.persist(updater);
      updater.commit();

      completedTasks.put(task);
      return Stream.empty();
    }
    return Stream.of(task);
  }
}
