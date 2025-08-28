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
import java.util.concurrent.CompletableFuture;

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
    // Get required services
    this.p2pService = context.getService(P2PService.class).orElse(null);
    this.synchronizationService = context.getService(SynchronizationService.class).orElse(null);

    // Register immediately when the plugin is loaded
    final Optional<HealthCheckService> healthCheckServiceOpt =
        context.getService(HealthCheckService.class);
    if (healthCheckServiceOpt.isPresent()) {
      this.healthCheckService = healthCheckServiceOpt.get();
      this.healthCheckService.registerReadinessCheckProvider(this);
      LOG.info("ReadinessCheckPlugin registered with HealthCheckService");
    } else {
      LOG.warn("HealthCheckService not available during registration");
    }

    if (p2pService == null) {
      LOG.warn("P2PService not available during registration");
    }
    if (synchronizationService == null) {
      LOG.warn("SynchronizationService not available during registration");
    }
  }

  @Override
  public void start() {
    // Registration already done in register() method
  }

  @Override
  public CompletableFuture<Void> reloadConfiguration() {
    // This plugin doesn't support dynamic reloading
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void stop() {
    LOG.info("ReadinessCheckPlugin stopped");
  }

  @Override
  public boolean isHealthy(final ParamSource paramSource) {
    try {
      // Check peer count
      int minPeers = getIntParam(paramSource, MIN_PEERS_PARAM, DEFAULT_MIN_PEERS);

      // Check if P2PService is available
      if (p2pService == null) {
        LOG.debug("P2PService not available, skipping peer count check");
        // If P2P is disabled or not available, we can't check peers, so assume ready
      } else {
        int peerCount = p2pService.getPeerCount();
        LOG.debug("Peer check - minPeers: {}, actual peerCount: {}", minPeers, peerCount);

        if (peerCount < minPeers) {
          LOG.debug(
              "Readiness check failed: peer count {} is below minimum {}", peerCount, minPeers);
          return false;
        }
      }

      // Check sync status using SynchronizationService
      int maxBlocksBehind =
          getIntParam(paramSource, MAX_BLOCKS_BEHIND_PARAM, DEFAULT_MAX_BLOCKS_BEHIND);

      // Check if SynchronizationService is available
      if (synchronizationService == null) {
        LOG.debug("SynchronizationService not available, skipping sync status check");
        // If sync service is not available, we can't check sync status, so assume ready
      } else {
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
      LOG.debug(
          "Invalid {} parameter: {}. Using default value: {}", paramName, paramValue, defaultValue);
      return defaultValue;
    }
  }
}
