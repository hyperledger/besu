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

import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.services.pipeline.Pipe;
import tech.pegasys.pantheon.services.tasks.Task;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;
import java.util.stream.Stream;

public class LoadLocalDataStep {

  private final WorldStateStorage worldStateStorage;
  private final Counter existingNodeCounter;

  public LoadLocalDataStep(
      final WorldStateStorage worldStateStorage, final MetricsSystem metricsSystem) {
    this.worldStateStorage = worldStateStorage;
    existingNodeCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.SYNCHRONIZER,
            "world_state_existing_nodes_total",
            "Total number of node data requests completed using existing data");
  }

  public Stream<Task<NodeDataRequest>> loadLocalData(
      final Task<NodeDataRequest> task, final Pipe<Task<NodeDataRequest>> completedTasks) {
    final NodeDataRequest request = task.getData();
    final Optional<BytesValue> existingData = request.getExistingData(worldStateStorage);
    if (existingData.isPresent()) {
      existingNodeCounter.inc();
      request.setData(existingData.get());
      request.setRequiresPersisting(false);
      completedTasks.put(task);
      return Stream.empty();
    }
    return Stream.of(task);
  }
}
