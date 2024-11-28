/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.metrics.opentelemetry;

import org.hyperledger.besu.metrics.MetricsService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Metrics open telemetry push service. */
public class MetricsOtelPushService implements MetricsService {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsOtelPushService.class);
  private final OpenTelemetrySystem metricsSystem;

  /**
   * Instantiates a new Metrics open telemetry push service.
   *
   * @param metricsSystem The OpenTelemetry metrics system
   */
  public MetricsOtelPushService(final OpenTelemetrySystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  @Override
  public CompletableFuture<?> start() {
    LOG.info("Starting OpenTelemetry push service");

    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<?> stop() {
    metricsSystem.shutdown();
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public Optional<Integer> getPort() {
    return Optional.empty();
  }
}
