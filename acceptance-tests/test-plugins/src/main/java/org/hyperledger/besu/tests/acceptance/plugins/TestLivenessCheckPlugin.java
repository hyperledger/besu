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
package org.hyperledger.besu.tests.acceptance.plugins;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.HealthCheckService;
import org.hyperledger.besu.plugin.services.health.LivenessCheckProvider;
import org.hyperledger.besu.plugin.services.health.ParamSource;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class TestLivenessCheckPlugin implements BesuPlugin, LivenessCheckProvider {

  private static final Logger LOG = LoggerFactory.getLogger(TestLivenessCheckPlugin.class);
  
  private HealthCheckService healthCheckService;
  
  // Test-specific state for acceptance testing
  private final AtomicBoolean livenessOverride = new AtomicBoolean(true);
  private final AtomicBoolean shouldFailLiveness = new AtomicBoolean(false);

  @Override
  public void register(final ServiceManager context) {
    LOG.info("Registering TestLivenessCheckPlugin");
    
    final Optional<HealthCheckService> healthCheckServiceOpt = context.getService(HealthCheckService.class);
    
    if (healthCheckServiceOpt.isPresent()) {
      this.healthCheckService = healthCheckServiceOpt.get();
      this.healthCheckService.registerLivenessCheckProvider(this);
      LOG.info("TestLivenessCheckPlugin registered with HealthCheckService");
    } else {
      LOG.warn("HealthCheckService not available during registration");
    }
  }

  @Override
  public void start() {
    LOG.info("TestLivenessCheckPlugin started");
  }

  @Override
  public void stop() {
    LOG.info("TestLivenessCheckPlugin stopped");
  }

  @Override
  public boolean isHealthy(final ParamSource paramSource) {
    if (shouldFailLiveness.get()) {
      LOG.debug("TestLivenessCheckPlugin liveness check failing (override)");
      return false;
    }
    return livenessOverride.get();
  }

  // Test control methods for acceptance testing
  public void setLivenessOverride(final boolean value) {
    livenessOverride.set(value);
  }

  public void setShouldFailLiveness(final boolean value) {
    shouldFailLiveness.set(value);
  }

  public boolean getLivenessOverride() {
    return livenessOverride.get();
  }
}
