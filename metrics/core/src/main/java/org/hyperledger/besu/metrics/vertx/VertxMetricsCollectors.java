/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.metrics.vertx;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

/**
 * Container for the metrics collectors used by Vertx
 *
 * @param submittedLabelledCounter counter for the number of tasks submitted
 * @param completedLabelledCounter counter for the number of tasks completed
 * @param rejectedLabelledCounter counter for the number of tasks rejected
 */
public record VertxMetricsCollectors(
    LabelledMetric<Counter> submittedLabelledCounter,
    LabelledMetric<Counter> completedLabelledCounter,
    LabelledMetric<Counter> rejectedLabelledCounter) {
  /**
   * Creates the collectors
   *
   * @param metricsSystem the metrics system
   * @return the record with all the collectors initialized
   */
  public static VertxMetricsCollectors create(final MetricsSystem metricsSystem) {
    final var submittedLabelledCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.NETWORK,
            "vertx_worker_pool_submitted_total",
            "Total number of tasks submitted to the Vertx worker pool",
            "poolType",
            "poolName");

    final var completedLabelledCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.NETWORK,
            "vertx_worker_pool_completed_total",
            "Total number of tasks completed by the Vertx worker pool",
            "poolType",
            "poolName");

    final var rejectedLabelledCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.NETWORK,
            "vertx_worker_pool_rejected_total",
            "Total number of tasks rejected by the Vertx worker pool",
            "poolType",
            "poolName");

    return new VertxMetricsCollectors(
        submittedLabelledCounter, completedLabelledCounter, rejectedLabelledCounter);
  }
}
