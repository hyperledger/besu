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
package org.hyperledger.besu.ethereum.eth.manager.bounded;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.concurrent.LinkedBlockingDeque;

public class BoundedQueue extends LinkedBlockingDeque<Runnable> {
  private final MetricsSystem metricsSystem;
  private final Counter totalEvictedTaskCounter;

  public BoundedQueue(
      final int capacity, final String metricName, final MetricsSystem metricsSystem) {
    super(capacity);
    this.metricsSystem = metricsSystem;
    this.totalEvictedTaskCounter =
        this.metricsSystem.createCounter(
            BesuMetricCategory.EXECUTORS,
            metricName + "_dropped_tasks_total",
            "Total number of tasks rejected by this working queue.");
  }

  @Override
  public boolean offer(final Runnable task) {
    while (!super.offer(task)) {
      remove();
      totalEvictedTaskCounter.inc();
    }
    return true;
  }
}
