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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.HealthCheckService;
import org.hyperledger.besu.plugin.services.health.ParamSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LivenessCheckPluginTest {

  @Mock private ServiceManager serviceManager;
  @Mock private HealthCheckService healthCheckService;
  @Mock private ParamSource paramSource;

  private LivenessCheckPlugin plugin;

  @BeforeEach
  void setUp() {
    plugin = new LivenessCheckPlugin();
  }

  @Test
  void shouldHaveCorrectName() {
    assertThat(plugin.getName()).isPresent();
    assertThat(plugin.getName().get()).contains("LivenessCheckPlugin");
  }

  @Test
  void shouldRegisterWithServiceManager() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(java.util.Optional.of(healthCheckService));

    plugin.register(serviceManager);

    verify(healthCheckService).registerLivenessCheckProvider(plugin);
  }

  @Test
  void shouldStartAndRegisterWithHealthCheckService() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(java.util.Optional.of(healthCheckService));

    plugin.register(serviceManager);
    plugin.start();

    // Verify that the plugin registered itself as a liveness check provider
    verify(healthCheckService).registerLivenessCheckProvider(plugin);
  }

  @Test
  void shouldAlwaysReturnTrueForLivenessCheck() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(java.util.Optional.of(healthCheckService));

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnTrueWithNullParamSource() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(java.util.Optional.of(healthCheckService));

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(null);
    assertThat(result).isTrue();
  }

  @Test
  void shouldStopGracefully() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(java.util.Optional.of(healthCheckService));

    plugin.register(serviceManager);
    plugin.start();

    // Should not throw any exceptions
    plugin.stop();
  }

  @Test
  void shouldHandleMissingHealthCheckService() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> plugin.register(serviceManager))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HealthCheckService not available");
  }
}
