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
import org.hyperledger.besu.plugin.services.health.ReadinessCheckProvider;
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class TestReadinessCheckPlugin implements BesuPlugin, ReadinessCheckProvider {

  private static final Logger LOG = LoggerFactory.getLogger(TestReadinessCheckPlugin.class);
  
  private HealthCheckService healthCheckService;
  private P2PService p2pService;
  private SynchronizationService synchronizationService;
  
  // Test-specific state for acceptance testing
  private final AtomicBoolean readinessOverride = new AtomicBoolean(true);
  private final AtomicBoolean shouldFailReadiness = new AtomicBoolean(false);

  @Override
  public void register(final ServiceManager context) {
    LOG.info("Registering TestReadinessCheckPlugin");
    
    final Optional<HealthCheckService> healthCheckServiceOpt = context.getService(HealthCheckService.class);
    final Optional<P2PService> p2pServiceOpt = context.getService(P2PService.class);
    final Optional<SynchronizationService> syncServiceOpt = context.getService(SynchronizationService.class);
    
    if (healthCheckServiceOpt.isPresent()) {
      this.healthCheckService = healthCheckServiceOpt.get();
      this.healthCheckService.registerReadinessCheckProvider(this);
      LOG.info("TestReadinessCheckPlugin registered with HealthCheckService");
    } else {
      LOG.warn("HealthCheckService not available during registration");
    }
    
    if (p2pServiceOpt.isPresent()) {
      this.p2pService = p2pServiceOpt.get();
    }
    
    if (syncServiceOpt.isPresent()) {
      this.synchronizationService = syncServiceOpt.get();
    }
  }

  @Override
  public void start() {
    LOG.info("TestReadinessCheckPlugin started");
  }

  @Override
  public void stop() {
    LOG.info("TestReadinessCheckPlugin stopped");
  }

  @Override
  public boolean isHealthy(final ParamSource paramSource) {
    if (shouldFailReadiness.get()) {
      LOG.debug("TestReadinessCheckPlugin readiness check failing (override)");
      return false;
    }
    
    try {
      // Get parameters with defaults
      int minPeers = getIntParam(paramSource, "minPeers", 1);
      int maxBlocksBehind = getIntParam(paramSource, "maxBlocksBehind", 10);
      
      // Check peer count if P2PService is available
      if (p2pService != null) {
        int peerCount = p2pService.getPeerCount();
        if (peerCount < minPeers) {
          LOG.debug("TestReadinessCheckPlugin readiness check failed: peer count {} < {}", peerCount, minPeers);
          return false;
        }
      }
      
      // Check sync status if SynchronizationService is available
      if (synchronizationService != null) {
        Optional<Long> highestBlock = synchronizationService.getHighestBlock();
        Optional<Long> currentBlock = synchronizationService.getCurrentBlock();
        if (highestBlock.isPresent() && currentBlock.isPresent()) {
          long blocksBehind = highestBlock.get() - currentBlock.get();
          if (blocksBehind > maxBlocksBehind) {
            LOG.debug("TestReadinessCheckPlugin readiness check failed: {} blocks behind > {}", blocksBehind, maxBlocksBehind);
            return false;
          }
        }
      }
      
      return readinessOverride.get();
    } catch (Exception e) {
      LOG.warn("TestReadinessCheckPlugin readiness check failed with exception: {}", e.getMessage(), e);
      return false;
    }
  }

  private int getIntParam(final ParamSource paramSource, final String paramName, final int defaultValue) {
    if (paramSource == null) {
      return defaultValue;
    }
    try {
      String value = paramSource.getParam(paramName);
      return value != null ? Integer.parseInt(value) : defaultValue;
    } catch (NumberFormatException e) {
      LOG.warn("Invalid value for parameter {}: {}", paramName, e.getMessage());
      return defaultValue;
    }
  }

  // Test control methods for acceptance testing
  public void setReadinessOverride(final boolean value) {
    readinessOverride.set(value);
  }

  public void setShouldFailReadiness(final boolean value) {
    shouldFailReadiness.set(value);
  }

  public boolean getReadinessOverride() {
    return readinessOverride.get();
  }
}
