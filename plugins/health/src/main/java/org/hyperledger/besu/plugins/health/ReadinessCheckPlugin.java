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
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.health.ReadinessCheckProvider;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;

import java.util.Optional;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin that provides readiness health checks for Besu.
 *
 * <p>This plugin implements readiness checks that verify:
 *
 * <ul>
 *   <li>Minimum number of connected peers
 *   <li>Synchronization status within acceptable distance from chain head
 * </ul>
 */
@AutoService(BesuPlugin.class)
public class ReadinessCheckPlugin implements BesuPlugin, ReadinessCheckProvider {
  private static final Logger LOG = LoggerFactory.getLogger(ReadinessCheckPlugin.class);

  private static final String MIN_PEERS_PARAM = "minPeers";
  private static final String MAX_BLOCKS_BEHIND_PARAM = "maxBlocksBehind";
  private static final int DEFAULT_MIN_PEERS = 1;
  private static final int DEFAULT_MAX_BLOCKS_BEHIND = 2;

  /** Default constructor for ReadinessCheckPlugin. */
  public ReadinessCheckPlugin() {}

  private HealthCheckService healthCheckService;
  private P2PService p2pService;
  private SynchronizationService synchronizationService;

  @Override
  public Optional<String> getName() {
    return Optional.of("ReadinessCheckPlugin");
  }

  @Override
  public void register(final ServiceManager context) {
    // Services may be unavailable in some configurations (e.g., p2p disabled). Defer checks to isHealthy.
    this.p2pService = context.getService(P2PService.class).orElse(null);
    this.synchronizationService = context.getService(SynchronizationService.class).orElse(null);

    // HealthCheckService is required to register the provider
    final Optional<HealthCheckService> healthCheckServiceOpt =
        context.getService(HealthCheckService.class);
    if (healthCheckServiceOpt.isPresent()) {
      this.healthCheckService = healthCheckServiceOpt.get();
      this.healthCheckService.registerReadinessCheckProvider(this);
      LOG.info("ReadinessCheckPlugin registered with HealthCheckService");
    } else {
      LOG.warn("HealthCheckService not available during registration");
    }
  }

  @Override
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }

  @Override
  public boolean isHealthy(final ParamSource paramSource) {
    try {
      // If HealthCheckService is not available, return true (assume healthy)
      if (healthCheckService == null) {
        LOG.debug("HealthCheckService not available; assuming healthy");
        return true;
      }

      // Check peer count (skip if p2pService is unavailable)
      int minPeers = getIntParam(paramSource, MIN_PEERS_PARAM, DEFAULT_MIN_PEERS);
      if (p2pService != null) {
        int peerCount = p2pService.getPeerCount();
        LOG.debug("Peer check - minPeers: {}, actual peerCount: {}", minPeers, peerCount);
        if (peerCount < minPeers) {
          LOG.debug(
              "Readiness check failed: peer count {} is below minimum {}", peerCount, minPeers);
          return false;
        }
      } else {
        LOG.debug("P2PService unavailable; skipping peer count check");
      }

      // Check sync status using SynchronizationService (skip if service unavailable)
      int maxBlocksBehind =
          getIntParam(paramSource, MAX_BLOCKS_BEHIND_PARAM, DEFAULT_MAX_BLOCKS_BEHIND);
      if (synchronizationService != null) {
        Optional<Long> highestBlock = synchronizationService.getHighestBlock();
        Optional<Long> currentBlock = synchronizationService.getCurrentBlock();

        LOG.debug(
            "Sync check - maxBlocksBehind: {}, highestBlock: {}, currentBlock: {}",
            maxBlocksBehind,
            highestBlock,
            currentBlock);

        if (highestBlock.isPresent() && currentBlock.isPresent()) {
          long blocksBehind = highestBlock.get() - currentBlock.get();
          LOG.debug("Calculated blocksBehind: {}", blocksBehind);

          if (blocksBehind > maxBlocksBehind) {
            LOG.debug(
                "Readiness check failed: {} blocks behind (max allowed: {})",
                blocksBehind,
                maxBlocksBehind);
            return false;
          }
          LOG.debug(
              "Sync status check passed: {} blocks behind (within limit: {})",
              blocksBehind,
              maxBlocksBehind);
        } else {
          LOG.debug("Sync status information not available, assuming in sync");
        }
      } else {
        LOG.debug("SynchronizationService unavailable; skipping sync status check");
      }

      return true;

    } catch (Exception e) {
      LOG.warn("Readiness check failed with exception: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Get an integer parameter from the parameter source, with fallback to default value.
   *
   * @param paramSource the parameter source
   * @param paramName the parameter name
   * @param defaultValue the default value to use if parameter is not found or invalid
   * @return the parameter value or default value
   * @throws IllegalArgumentException if the parameter value is present but not a valid integer
   */
  private int getIntParam(
      final ParamSource paramSource, final String paramName, final int defaultValue) {
    if (paramSource == null) {
      LOG.debug("ParamSource is null for {}, using default: {}", paramName, defaultValue);
      return defaultValue;
    }
    String paramValue = paramSource.getParam(paramName);
    LOG.debug("Parameter {} = '{}' (default: {})", paramName, paramValue, defaultValue);

    if (paramValue == null) {
      LOG.debug("Parameter {} is null, using default: {}", paramName, defaultValue);
      return defaultValue;
    }

    try {
      int result = Integer.parseInt(paramValue);
      LOG.debug("Parameter {} parsed as: {}", paramName, result);
      return result;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("Invalid integer for parameter '%s': '%s'", paramName, paramValue), e);
    }
  }
}
