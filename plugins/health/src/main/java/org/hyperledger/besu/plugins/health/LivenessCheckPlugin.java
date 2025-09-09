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
package org.hyperledger.besu.plugins.health;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.HealthCheckService;
import org.hyperledger.besu.plugin.services.health.LivenessCheckProvider;
import org.hyperledger.besu.plugin.services.health.ParamSource;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin that provides liveness health checks for Besu.
 *
 * <p>This plugin implements a simple liveness check that always returns true, indicating that the
 * Besu process is alive and running.
 */
@AutoService(BesuPlugin.class)
public class LivenessCheckPlugin implements BesuPlugin, LivenessCheckProvider {
  private static final Logger LOG = LoggerFactory.getLogger(LivenessCheckPlugin.class);

  private HealthCheckService healthCheckService;

  /** Default constructor for LivenessCheckPlugin. */
  public LivenessCheckPlugin() {}

  @Override
  public Optional<String> getName() {
    return Optional.of("LivenessCheckPlugin");
  }

  @Override
  public void register(final ServiceManager context) {
    // Register immediately when the plugin is loaded
    final Optional<HealthCheckService> healthCheckServiceOpt =
        context.getService(HealthCheckService.class);
    if (healthCheckServiceOpt.isPresent()) {
      this.healthCheckService = healthCheckServiceOpt.get();
      this.healthCheckService.registerLivenessCheckProvider(this);
      LOG.info("LivenessCheckPlugin registered with HealthCheckService");
    } else {
      throw new IllegalStateException(
          "HealthCheckService not available during LivenessCheckPlugin registration");
    }
  }

  @Override
  public boolean isHealthy(final ParamSource paramSource) {
    // Simple check - always return true as long as the process is running
    return true;
  }

  @Override
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }
}
