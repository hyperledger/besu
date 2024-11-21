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
package org.hyperledger.besu.ethereum.api.jsonrpc.metrics;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

public record RpcTraceMetricsCollectors(
    LabelledMetric<Counter> traceBlockTxsProcessedCounter,
    LabelledMetric<Counter> traceFilterTxsProcessedCounter,
    LabelledMetric<Counter> traceReplayBlockTxsProcessedCounter) {
  public static RpcTraceMetricsCollectors create(final MetricsSystem metricsSystem) {
    final var traceBlockTxsProcessedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "transactions_traceblock_pipeline_processed_total",
            "Number of transactions processed for each block",
            "step",
            "action");

    final var traceFilterTxsProcessedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "transactions_tracefilter_pipeline_processed_total",
            "Number of transactions processed for trace_filter",
            "step",
            "action");

    final var traceReplayBlockTxsProcessedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "transactions_tracereplayblock_pipeline_processed_total",
            "Number of transactions processed for each block",
            "step",
            "action");

    return new RpcTraceMetricsCollectors(
        traceBlockTxsProcessedCounter,
        traceFilterTxsProcessedCounter,
        traceReplayBlockTxsProcessedCounter);
  }
}
