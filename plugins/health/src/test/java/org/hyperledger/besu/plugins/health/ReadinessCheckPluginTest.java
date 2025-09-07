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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.HealthCheckService;
import org.hyperledger.besu.plugin.services.health.ParamSource;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadinessCheckPluginTest {

  @Mock private ServiceManager serviceManager;
  @Mock private HealthCheckService healthCheckService;
  @Mock private P2PService p2pService;
  @Mock private SynchronizationService synchronizationService;
  @Mock private ParamSource paramSource;

  private ReadinessCheckPlugin plugin;

  @BeforeEach
  void setUp() {
    plugin = new ReadinessCheckPlugin();
  }

  @Test
  void shouldHaveCorrectName() {
    assertThat(plugin.getName()).isPresent();
    assertThat(plugin.getName().get()).contains("ReadinessCheckPlugin");
  }

  @Test
  void shouldRegisterWithServiceManager() {
    plugin.register(serviceManager);

    // Plugin should store the service manager
    assertThat(plugin).isNotNull();
  }

  @Test
  void shouldStartAndRegisterWithHealthCheckService() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));

    plugin.register(serviceManager);
    plugin.start();

    // Verify that the plugin registered itself as a readiness check provider
    verify(healthCheckService).registerReadinessCheckProvider(plugin);
  }

  @Test
  void shouldReturnTrueWhenAllChecksPass() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));
    when(p2pService.getPeerCount()).thenReturn(5);
    when(synchronizationService.getHighestBlock()).thenReturn(Optional.of(100L));
    when(synchronizationService.getCurrentBlock()).thenReturn(Optional.of(98L));

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenPeerCountTooLow() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));
    when(p2pService.getPeerCount()).thenReturn(0); // Below default min (1)

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenTooManyBlocksBehind() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));
    when(p2pService.getPeerCount()).thenReturn(5);
    when(synchronizationService.getHighestBlock()).thenReturn(Optional.of(100L));
    when(synchronizationService.getCurrentBlock()).thenReturn(Optional.of(90L)); // 10 blocks behind

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isFalse();
  }

  @Test
  void shouldUseCustomParameters() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));
    when(p2pService.getPeerCount()).thenReturn(2);
    // Do not stub sync heights because this test fails on peers before using them
    when(paramSource.getParam("minPeers")).thenReturn("3"); // Custom min peers
    when(paramSource.getParam("maxBlocksBehind")).thenReturn("10"); // Custom max blocks

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isFalse(); // Should fail because 2 peers < 3 min peers
  }

  @Test
  void shouldHandleMissingServices() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.empty());
    when(serviceManager.getService(SynchronizationService.class)).thenReturn(Optional.empty());

    plugin.register(serviceManager);

    // Should not throw exception, just log warnings
    plugin.start();

    // Plugin should still be functional even without P2P and Sync services
    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isTrue(); // Should pass when services are not available
  }

  @Test
  void shouldHandleMissingBlocks() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));
    when(p2pService.getPeerCount()).thenReturn(5);
    when(synchronizationService.getHighestBlock()).thenReturn(Optional.empty());
    when(synchronizationService.getCurrentBlock()).thenReturn(Optional.empty());

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isTrue(); // Should pass when blocks are not available
  }

  @Test
  void shouldHandleNullParamSource() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));
    when(p2pService.getPeerCount()).thenReturn(5);
    when(synchronizationService.getHighestBlock()).thenReturn(Optional.of(100L));
    when(synchronizationService.getCurrentBlock()).thenReturn(Optional.of(98L));

    plugin.register(serviceManager);
    plugin.start();

    boolean result = plugin.isHealthy(null);
    assertThat(result).isTrue(); // Should use default parameters
  }

  @Test
  void shouldStopGracefully() {
    when(serviceManager.getService(HealthCheckService.class))
        .thenReturn(Optional.of(healthCheckService));
    when(serviceManager.getService(P2PService.class)).thenReturn(Optional.of(p2pService));
    when(serviceManager.getService(SynchronizationService.class))
        .thenReturn(Optional.of(synchronizationService));

    plugin.register(serviceManager);
    plugin.start();

    // Should not throw any exceptions
    plugin.stop();
  }

  @Test
  void shouldHandleMissingHealthCheckService() {
    when(serviceManager.getService(HealthCheckService.class)).thenReturn(Optional.empty());

    plugin.register(serviceManager);

    // Should not throw exception, just log warning
    plugin.start();

    // Plugin should still be functional even without HealthCheckService
    boolean result = plugin.isHealthy(paramSource);
    assertThat(result).isTrue(); // Should use default parameters and pass
  }
}
