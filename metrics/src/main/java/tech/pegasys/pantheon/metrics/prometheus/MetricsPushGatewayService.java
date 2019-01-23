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
package tech.pegasys.pantheon.metrics.prometheus;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.NetworkUtility;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.prometheus.client.exporter.PushGateway;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class MetricsPushGatewayService implements MetricsService {
  private static final Logger LOG = LogManager.getLogger();

  private PushGateway pushGateway;
  private ScheduledExecutorService scheduledExecutorService;
  private final MetricsConfiguration config;
  private final MetricsSystem metricsSystem;

  MetricsPushGatewayService(
      final MetricsConfiguration configuration, final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    validateConfig(configuration);
    config = configuration;
  }

  private void validateConfig(final MetricsConfiguration config) {
    checkArgument(
        config.getPort() == 0 || NetworkUtility.isValidPort(config.getPort()),
        "Invalid port configuration.");
    checkArgument(config.getHost() != null, "Required host is not configured.");
    checkArgument(
        MetricsConfiguration.MODE_PUSH_GATEWAY.equals(config.getMode()),
        "Metrics Push Gateway Service cannot start up outside of '"
            + MetricsConfiguration.MODE_PUSH_GATEWAY
            + "' mode.");
    checkArgument(
        metricsSystem instanceof PrometheusMetricsSystem,
        "Push Gateway requires a Prometheus Metrics System.");
  }

  @Override
  public CompletableFuture<?> start() {
    LOG.info("Starting Metrics service on {}:{}", config.getHost(), config.getPort());

    pushGateway = new PushGateway(config.getHost() + ":" + config.getPort());

    // Create the executor
    scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleAtFixedRate(
        this::pushMetrics,
        config.getPushInterval() / 2,
        config.getPushInterval(),
        TimeUnit.SECONDS);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<?> stop() {
    final CompletableFuture<?> resultFuture = new CompletableFuture<>();
    try {
      // Calling shutdown now cancels the pending push, which is desirable.
      scheduledExecutorService.shutdownNow();
      scheduledExecutorService.awaitTermination(30, TimeUnit.SECONDS);
      pushGateway.delete(config.getPrometheusJob());
      resultFuture.complete(null);
    } catch (final IOException | InterruptedException e) {
      LOG.error(e);
      resultFuture.completeExceptionally(e);
    }
    return resultFuture;
  }

  @Override
  public Optional<Integer> getPort() {
    return Optional.empty();
  }

  private void pushMetrics() {
    try {
      pushGateway.pushAdd(
          ((PrometheusMetricsSystem) metricsSystem).getRegistry(), config.getPrometheusJob());
    } catch (final IOException e) {
      LOG.warn("Cound not push metrics", e);
    }
  }
}
