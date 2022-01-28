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
 *
 */
package org.hyperledger.besu.metrics.opentelemetry;

import org.hyperledger.besu.metrics.MetricsService;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReaderBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsOtelGrpcPushService implements MetricsService {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsOtelGrpcPushService.class);

  private final MetricsConfiguration configuration;
  private final OpenTelemetrySystem metricsSystem;
  private IntervalMetricReader periodicReader;
  private SpanProcessor spanProcessor;

  public MetricsOtelGrpcPushService(
      final MetricsConfiguration configuration, final OpenTelemetrySystem metricsSystem) {

    this.configuration = configuration;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<?> start() {
    LOG.info("Starting OpenTelemetry push service");
    OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.getDefault();
    IntervalMetricReaderBuilder builder =
        IntervalMetricReader.builder()
            .setExportIntervalMillis(configuration.getPushInterval() * 1000L)
            .setMetricProducers(Collections.singleton(metricsSystem.getMeterSdkProvider()))
            .setMetricExporter(exporter);
    this.periodicReader = builder.buildAndStart();
    this.spanProcessor = BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().build()).build();
    OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build())
        .buildAndRegisterGlobal();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<?> stop() {
    if (periodicReader != null) {
      periodicReader.shutdown();
    }
    if (spanProcessor != null) {
      CompletableResultCode result = spanProcessor.shutdown();
      CompletableFuture<?> future = new CompletableFuture<>();
      result.whenComplete(() -> future.complete(null));
      return future;
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public Optional<Integer> getPort() {
    return Optional.empty();
  }
}
