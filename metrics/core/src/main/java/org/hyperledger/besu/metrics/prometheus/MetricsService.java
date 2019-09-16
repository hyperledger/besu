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
package org.hyperledger.besu.metrics.prometheus;

import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;

public interface MetricsService {

  static MetricsService create(
      final Vertx vertx,
      final MetricsConfiguration configuration,
      final MetricsSystem metricsSystem) {
    if (configuration.isEnabled()) {
      return new MetricsHttpService(vertx, configuration, metricsSystem);
    } else if (configuration.isPushEnabled()) {
      return new MetricsPushGatewayService(configuration, metricsSystem);
    } else {
      throw new RuntimeException("No metrics service enabled.");
    }
  }

  /**
   * Starts the Metrics Service and all needed backend systems.
   *
   * @return completion state
   */
  CompletableFuture<?> start();

  /**
   * Stops the Metrics Service and performs needed cleanup.
   *
   * @return completion state
   */
  CompletableFuture<?> stop();

  /**
   * If serving to a port on the client, what the port number is.
   *
   * @return Port number optional, serving if present.
   */
  Optional<Integer> getPort();
}
