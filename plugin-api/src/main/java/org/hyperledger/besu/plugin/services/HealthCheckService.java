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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.health.LivenessCheckProvider;
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.health.ReadinessCheckProvider;

/**
 * Service for registering health check providers.
 *
 * <p>This service allows plugins to register health check implementations that can be used by
 * Besu's health endpoints.
 */
public interface HealthCheckService extends BesuService {

  /**
   * Register a liveness check provider.
   *
   * @param provider the liveness check provider to register
   */
  void registerLivenessCheckProvider(LivenessCheckProvider provider);

  /**
   * Register a readiness check provider.
   *
   * @param provider the readiness check provider to register
   */
  void registerReadinessCheckProvider(ReadinessCheckProvider provider);

  /**
   * Get all registered liveness check providers.
   *
   * @return list of liveness check providers
   */
  java.util.List<LivenessCheckProvider> getLivenessCheckProviders();

  /**
   * Get all registered readiness check providers.
   *
   * @return list of readiness check providers
   */
  java.util.List<ReadinessCheckProvider> getReadinessCheckProviders();

  /**
   * Check if the system is live.
   *
   * @param paramSource source for health check parameters
   * @return true if the system is live, false otherwise
   */
  boolean isLive(ParamSource paramSource);

  /**
   * Check if the system is ready.
   *
   * @param paramSource source for health check parameters
   * @return true if the system is ready, false otherwise
   */
  boolean isReady(ParamSource paramSource);
}
