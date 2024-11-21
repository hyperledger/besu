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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.opentelemetry.OpenTelemetrySystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.function.IntSupplier;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public class RpcMetrics {
  private final ObservableMetricsSystem metricsSystem;
  private final Tracer tracer;
  private final RpcTraceMetricsCollectors rpcTraceMetricsCollectors;
  private final LabelledMetric<OperationTimer> requestTimer;
  private final LabelledMetric<Counter> errorsCounter;

  public RpcMetrics(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    this.tracer = getTracer(metricsSystem);
    this.rpcTraceMetricsCollectors = RpcTraceMetricsCollectors.create(metricsSystem);

    this.requestTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.RPC,
            "request_time",
            "Time taken to process a JSON-RPC request",
            "methodName");

    this.errorsCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.RPC,
            "errors_count",
            "Number of errors per RPC method and RPC error type",
            "rpcMethod",
            "errorType");
  }

  public void initActiveHttpConnectionCounter(final IntSupplier activeHttpConnectionCountSupplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.RPC,
        "active_http_connection_count",
        "Total no of active rpc http connections",
        activeHttpConnectionCountSupplier);
  }

  private Tracer getTracer(final MetricsSystem metricsSystem) {
    return (metricsSystem instanceof OpenTelemetrySystem openTelemetrySystem)
        ? openTelemetrySystem.getTracerProvider().get("org.hyperledger.besu.jsonrpc", "1.0.0")
        : OpenTelemetry.noop().getTracer("org.hyperledger.besu.jsonrpc", "1.0.0");
  }

  public ObservableMetricsSystem getObservableMetricsSystem() {
    return metricsSystem;
  }

  public Tracer getTracer() {
    return tracer;
  }

  public RpcTraceMetricsCollectors getRpcTraceMetrics() {
    return rpcTraceMetricsCollectors;
  }

  public void incErrorsCounter(
      final JsonRpcMethod method, final JsonRpcErrorResponse errorResponse) {
    errorsCounter.labels(method.getName(), errorResponse.getErrorType().name()).inc();
  }

  public OperationTimer.TimingContext startRequestTimer(final JsonRpcRequest request) {
    return requestTimer.labels(request.getMethod()).startTimer();
  }
}
