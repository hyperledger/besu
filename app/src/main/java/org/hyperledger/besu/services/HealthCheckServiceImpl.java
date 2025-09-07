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
package org.hyperledger.besu.services;

import org.hyperledger.besu.plugin.services.HealthCheckService;
import org.hyperledger.besu.plugin.services.health.LivenessCheckProvider;
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.health.ReadinessCheckProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of the {@link HealthCheckService}. */
public class HealthCheckServiceImpl implements HealthCheckService {
  private static final Logger LOG = LoggerFactory.getLogger(HealthCheckServiceImpl.class);

  private final List<LivenessCheckProvider> livenessCheckProviders = new CopyOnWriteArrayList<>();
  private final List<ReadinessCheckProvider> readinessCheckProviders = new CopyOnWriteArrayList<>();

  /** Default constructor. */
  public HealthCheckServiceImpl() {}

  @Override
  public void registerLivenessCheckProvider(final LivenessCheckProvider provider) {
    if (provider != null) {
      livenessCheckProviders.add(provider);
      LOG.debug("Registered liveness check provider: {}", provider.getClass().getSimpleName());
    }
  }

  @Override
  public void registerReadinessCheckProvider(final ReadinessCheckProvider provider) {
    if (provider != null) {
      readinessCheckProviders.add(provider);
      LOG.debug("Registered readiness check provider: {}", provider.getClass().getSimpleName());
    }
  }

  @Override
  public List<LivenessCheckProvider> getLivenessCheckProviders() {
    return new ArrayList<>(livenessCheckProviders);
  }

  @Override
  public List<ReadinessCheckProvider> getReadinessCheckProviders() {
    return new ArrayList<>(readinessCheckProviders);
  }

  @Override
  public boolean isLive(final ParamSource paramSource) {
    if (livenessCheckProviders.isEmpty()) {
      throw new IllegalStateException(
          "No liveness providers registered. Ensure at least one provider is registered by a plugin.");
    }
    return livenessCheckProviders.stream()
        .allMatch(provider -> executeLivenessProviderSafely(provider, paramSource));
  }

  @Override
  public boolean isReady(final ParamSource paramSource) {
    if (readinessCheckProviders.isEmpty()) {
      throw new IllegalStateException(
          "No readiness providers registered. Ensure at least one provider is registered by a plugin.");
    }
    return readinessCheckProviders.stream()
        .allMatch(provider -> executeReadinessProviderSafely(provider, paramSource));
  }

  private boolean executeLivenessProviderSafely(
      final LivenessCheckProvider provider, final ParamSource paramSource) {
    try {
      return provider.isHealthy(paramSource);
    } catch (final Exception e) {
      LOG.warn("Liveness check failed: {}", e.getMessage(), e);
      return false;
    }
  }

  private boolean executeReadinessProviderSafely(
      final ReadinessCheckProvider provider, final ParamSource paramSource) {
    try {
      return provider.isHealthy(paramSource);
    } catch (final Exception e) {
      LOG.warn("Readiness check failed: {}", e.getMessage(), e);
      return false;
    }
  }

  @VisibleForTesting
  void clearProviders() {
    livenessCheckProviders.clear();
    readinessCheckProviders.clear();
  }
}
