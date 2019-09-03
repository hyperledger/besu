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
package tech.pegasys.pantheon.metrics.vertx;

import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;

import io.vertx.core.spi.metrics.PoolMetrics;

final class PoolMetricsAdapter implements PoolMetrics<Object> {

  private final Counter submittedCounter;
  private final Counter completedCounter;
  private final Counter rejectedCounter;

  public PoolMetricsAdapter(
      final MetricsSystem metricsSystem, final String poolType, final String poolName) {
    submittedCounter =
        metricsSystem
            .createLabelledCounter(
                PantheonMetricCategory.NETWORK,
                "vertx_worker_pool_submitted_total",
                "Total number of tasks submitted to the Vertx worker pool",
                "poolType",
                "poolName")
            .labels(poolType, poolName);

    completedCounter =
        metricsSystem
            .createLabelledCounter(
                PantheonMetricCategory.NETWORK,
                "vertx_worker_pool_completed_total",
                "Total number of tasks completed by the Vertx worker pool",
                "poolType",
                "poolName")
            .labels(poolType, poolName);

    rejectedCounter =
        metricsSystem
            .createLabelledCounter(
                PantheonMetricCategory.NETWORK,
                "vertx_worker_pool_rejected_total",
                "Total number of tasks rejected by the Vertx worker pool",
                "poolType",
                "poolName")
            .labels(poolType, poolName);
  }

  @Override
  public Object submitted() {
    submittedCounter.inc();
    return null;
  }

  @Override
  public void rejected(final Object o) {
    rejectedCounter.inc();
  }

  @Override
  public void end(final Object o, final boolean succeeded) {
    completedCounter.inc();
  }
}
