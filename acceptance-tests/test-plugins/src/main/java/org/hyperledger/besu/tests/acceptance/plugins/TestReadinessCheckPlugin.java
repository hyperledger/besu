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
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.health.ReadinessCheckProvider;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class TestReadinessCheckPlugin implements BesuPlugin, ReadinessCheckProvider {

  private static final Logger LOG = LoggerFactory.getLogger(TestReadinessCheckPlugin.class);

  private HealthCheckService healthCheckService;
  private P2PService p2pService;
  private SynchronizationService synchronizationService;

  // Test-specific state for acceptance testing
  private final AtomicBoolean readinessOverride = new AtomicBoolean(true);
  private final AtomicBoolean shouldFailReadiness = new AtomicBoolean(false);

  // CLI-configurable options to control readiness from ATs (separate JVM)
  @Option(
      names = {"--plugin-health-readiness-min-peers"},
      description = "Minimum peers required for readiness",
      hidden = true,
      defaultValue = "1")
  int minPeersFlag = 1;

  @Option(
      names = {"--plugin-health-readiness-max-blocks-behind"},
      description = "Maximum blocks behind for readiness",
      hidden = true,
      defaultValue = "10")
  int maxBlocksBehindFlag = 10;

  @Option(
      names = {"--plugin-health-readiness-down"},
      description = "Force readiness to DOWN for testing",
      hidden = true,
      defaultValue = "false")
  boolean readinessDownFlag = false;

  @Override
  public void register(final ServiceManager context) {
    LOG.info("Registering TestReadinessCheckPlugin");

    // All these services are required - fail fast if any are missing
    this.p2pService =
        context
            .getService(P2PService.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "P2PService is not available - this indicates a serious internal error"));

    this.synchronizationService =
        context
            .getService(SynchronizationService.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "SynchronizationService is not available - this indicates a serious internal error"));

    this.healthCheckService =
        context
            .getService(HealthCheckService.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "HealthCheckService is not available - this indicates a serious internal error"));

    // Register the readiness check provider - guaranteed to work since healthCheckService is
    // non-null
    this.healthCheckService.registerReadinessCheckProvider(this);
    LOG.info("TestReadinessCheckPlugin registered successfully with all required services");

    // Expose CLI options so ATs can pass flags to control behavior cross-process
    context
        .getService(PicoCLIOptions.class)
        .ifPresent(pico -> pico.addPicoCLIOptions("test-health", this));
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
    if (readinessDownFlag) {
      LOG.debug("TestReadinessCheckPlugin readiness forced DOWN via CLI flag");
      return false;
    }

    if (shouldFailReadiness.get()) {
      LOG.debug("TestReadinessCheckPlugin readiness check failing (override)");
      return false;
    }

    try {
      // Get parameters with defaults (CLI flags take precedence over HTTP params)
      int minPeers = getIntParam(paramSource, "minPeers", minPeersFlag);
      int maxBlocksBehind = getIntParam(paramSource, "maxBlocksBehind", maxBlocksBehindFlag);

      // Check peer count - P2PService is guaranteed to be non-null due to fail-fast registration
      int peerCount = p2pService.getPeerCount();
      if (peerCount < minPeers) {
        LOG.debug(
            "TestReadinessCheckPlugin readiness check failed: peer count {} < {}",
            peerCount,
            minPeers);
        return false;
      }

      // Check sync status - SynchronizationService is guaranteed to be non-null due to fail-fast
      // registration
      Optional<Long> highestBlock = synchronizationService.getHighestBlock();
      Optional<Long> currentBlock = synchronizationService.getCurrentBlock();
      if (highestBlock.isPresent() && currentBlock.isPresent()) {
        long blocksBehind = highestBlock.get() - currentBlock.get();
        if (blocksBehind > maxBlocksBehind) {
          LOG.debug(
              "TestReadinessCheckPlugin readiness check failed: {} blocks behind > {}",
              blocksBehind,
              maxBlocksBehind);
          return false;
        }
      }

      return readinessOverride.get();
    } catch (Exception e) {
      LOG.warn(
          "TestReadinessCheckPlugin readiness check failed with exception: {}", e.getMessage(), e);
      return false;
    }
  }

  private int getIntParam(
      final ParamSource paramSource, final String paramName, final int defaultValue) {
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
