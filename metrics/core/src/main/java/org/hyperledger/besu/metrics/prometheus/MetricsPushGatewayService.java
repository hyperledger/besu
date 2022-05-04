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
package org.hyperledger.besu.metrics.prometheus;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.metrics.MetricsService;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.prometheus.client.exporter.PushGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsPushGatewayService implements MetricsService {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsPushGatewayService.class);

  private PushGateway pushGateway;
  private ScheduledExecutorService scheduledExecutorService;
  private final MetricsConfiguration config;
  private final MetricsSystem metricsSystem;

  public MetricsPushGatewayService(
      final MetricsConfiguration configuration, final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    validateConfig(configuration);
    config = configuration;
  }

  private void validateConfig(final MetricsConfiguration config) {
    checkArgument(
        config.getPushPort() >= 0 && config.getPushPort() < 65536, "Invalid port configuration.");
    checkArgument(config.getPushHost() != null, "Required host is not configured.");
    checkArgument(
        !(config.isEnabled() && config.isPushEnabled()),
        "Metrics Push Gateway Service cannot run concurrent with the normal metrics.");
    checkArgument(
        metricsSystem instanceof PrometheusMetricsSystem,
        "Push Gateway requires a Prometheus Metrics System.");
  }

  @Override
  public CompletableFuture<?> start() {
    LOG.info(
        "Starting metrics push gateway service pushing to {}:{}",
        config.getPushHost(),
        config.getPushPort());

    pushGateway = new PushGateway(config.getPushHost() + ":" + config.getPushPort());

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
      try {
        pushGateway.delete(config.getPrometheusJob());
      } catch (final Exception e) {
        LOG.error("Could not clean up results on the Prometheus Push Gateway.", e);
        // Do not complete exceptionally, the gateway may be down and failures
        // here cause the shutdown to loop.  Failure is acceptable.
      }
      resultFuture.complete(null);
    } catch (final InterruptedException e) {
      LOG.error("Unable to shutdown push metrics service gracefully", e);
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
      LOG.warn("Could not push metrics", e);
    }
  }
}
