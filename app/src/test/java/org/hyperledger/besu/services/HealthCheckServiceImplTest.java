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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.plugin.services.health.LivenessCheckProvider;
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.health.ReadinessCheckProvider;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceImplTest {

  @Mock private LivenessCheckProvider livenessProvider1;
  @Mock private LivenessCheckProvider livenessProvider2;
  @Mock private ReadinessCheckProvider readinessProvider1;
  @Mock private ReadinessCheckProvider readinessProvider2;
  @Mock private ParamSource paramSource;

  private HealthCheckServiceImpl healthCheckService;

  @BeforeEach
  void setUp() {
    healthCheckService = new HealthCheckServiceImpl();
  }

  @Test
  void shouldStartWithEmptyProviders() {
    assertThat(healthCheckService.getLivenessCheckProviders()).isEmpty();
    assertThat(healthCheckService.getReadinessCheckProviders()).isEmpty();
  }

  @Test
  void shouldRegisterLivenessCheckProvider() {
    healthCheckService.registerLivenessCheckProvider(livenessProvider1);

    List<LivenessCheckProvider> providers = healthCheckService.getLivenessCheckProviders();
    assertThat(providers).hasSize(1);
    assertThat(providers).contains(livenessProvider1);
  }

  @Test
  void shouldRegisterMultipleLivenessCheckProviders() {
    healthCheckService.registerLivenessCheckProvider(livenessProvider1);
    healthCheckService.registerLivenessCheckProvider(livenessProvider2);

    List<LivenessCheckProvider> providers = healthCheckService.getLivenessCheckProviders();
    assertThat(providers).hasSize(2);
    assertThat(providers).contains(livenessProvider1, livenessProvider2);
  }

  @Test
  void shouldRegisterReadinessCheckProvider() {
    healthCheckService.registerReadinessCheckProvider(readinessProvider1);

    List<ReadinessCheckProvider> providers = healthCheckService.getReadinessCheckProviders();
    assertThat(providers).hasSize(1);
    assertThat(providers).contains(readinessProvider1);
  }

  @Test
  void shouldRegisterMultipleReadinessCheckProviders() {
    healthCheckService.registerReadinessCheckProvider(readinessProvider1);
    healthCheckService.registerReadinessCheckProvider(readinessProvider2);

    List<ReadinessCheckProvider> providers = healthCheckService.getReadinessCheckProviders();
    assertThat(providers).hasSize(2);
    assertThat(providers).contains(readinessProvider1, readinessProvider2);
  }

  @Test
  void shouldThrowExceptionWhenNoLivenessProviders() {
    assertThatThrownBy(() -> healthCheckService.isLive(paramSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No liveness providers registered");
  }

  @Test
  void shouldReturnTrueWhenAllLivenessProvidersPass() {
    when(livenessProvider1.isHealthy(paramSource)).thenReturn(true);
    when(livenessProvider2.isHealthy(paramSource)).thenReturn(true);

    healthCheckService.registerLivenessCheckProvider(livenessProvider1);
    healthCheckService.registerLivenessCheckProvider(livenessProvider2);

    boolean result = healthCheckService.isLive(paramSource);
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenAnyLivenessProviderFails() {
    when(livenessProvider1.isHealthy(paramSource)).thenReturn(true);
    when(livenessProvider2.isHealthy(paramSource)).thenReturn(false);

    healthCheckService.registerLivenessCheckProvider(livenessProvider1);
    healthCheckService.registerLivenessCheckProvider(livenessProvider2);

    boolean result = healthCheckService.isLive(paramSource);
    assertThat(result).isFalse();
  }

  @Test
  void shouldThrowExceptionWhenNoReadinessProviders() {
    assertThatThrownBy(() -> healthCheckService.isReady(paramSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No readiness providers registered");
  }

  @Test
  void shouldReturnTrueWhenAllReadinessProvidersPass() {
    when(readinessProvider1.isHealthy(paramSource)).thenReturn(true);
    when(readinessProvider2.isHealthy(paramSource)).thenReturn(true);

    healthCheckService.registerReadinessCheckProvider(readinessProvider1);
    healthCheckService.registerReadinessCheckProvider(readinessProvider2);

    boolean result = healthCheckService.isReady(paramSource);
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenAnyReadinessProviderFails() {
    when(readinessProvider1.isHealthy(paramSource)).thenReturn(true);
    when(readinessProvider2.isHealthy(paramSource)).thenReturn(false);

    healthCheckService.registerReadinessCheckProvider(readinessProvider1);
    healthCheckService.registerReadinessCheckProvider(readinessProvider2);

    boolean result = healthCheckService.isReady(paramSource);
    assertThat(result).isFalse();
  }

  @Test
  void shouldHandleLivenessProviderExceptionGracefully() {
    when(livenessProvider1.isHealthy(paramSource))
        .thenThrow(new RuntimeException("Test exception"));

    healthCheckService.registerLivenessCheckProvider(livenessProvider1);

    boolean result = healthCheckService.isLive(paramSource);
    assertThat(result).isFalse();
  }

  @Test
  void shouldHandleReadinessProviderExceptionGracefully() {
    when(readinessProvider1.isHealthy(paramSource))
        .thenThrow(new RuntimeException("Test exception"));

    healthCheckService.registerReadinessCheckProvider(readinessProvider1);

    boolean result = healthCheckService.isReady(paramSource);
    assertThat(result).isFalse();
  }

  @Test
  void shouldHandleNullParamSource() {
    when(livenessProvider1.isHealthy(null)).thenReturn(true);
    healthCheckService.registerLivenessCheckProvider(livenessProvider1);

    boolean result = healthCheckService.isLive(null);
    assertThat(result).isTrue();
  }
}
