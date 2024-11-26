/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.metrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.util.Set;

import org.junit.jupiter.api.Test;

class MetricCategoryRegistryImplTest {

  @Test
  void metricCategoryIsEnabledAndMetricsAreEnabled() {
    final var registry = new MetricCategoryRegistryImpl();
    registry.addMetricCategory(BesuMetricCategory.BLOCKCHAIN);
    final var metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(true)
            .metricCategories(Set.of(BesuMetricCategory.BLOCKCHAIN))
            .build();
    registry.setMetricsConfiguration(metricsConfiguration);
    assertTrue(registry.isMetricCategoryEnabled(BesuMetricCategory.BLOCKCHAIN));
  }

  @Test
  void metricCategoryIsDisabledAndMetricsAreEnabled() {
    final var registry = new MetricCategoryRegistryImpl();
    registry.addMetricCategory(BesuMetricCategory.ETHEREUM);
    final var metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(true)
            .metricCategories(Set.of(BesuMetricCategory.ETHEREUM))
            .build();
    registry.setMetricsConfiguration(metricsConfiguration);
    assertFalse(registry.isMetricCategoryEnabled(BesuMetricCategory.BLOCKCHAIN));
  }

  @Test
  void metricCategoryNotEnabledWhenMetricsAreDisabled() {
    final var registry = new MetricCategoryRegistryImpl();
    registry.addMetricCategory(BesuMetricCategory.BLOCKCHAIN);
    final var metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(false)
            .metricCategories(Set.of(BesuMetricCategory.BLOCKCHAIN))
            .build();
    registry.setMetricsConfiguration(metricsConfiguration);
    assertFalse(registry.isMetricCategoryEnabled(BesuMetricCategory.BLOCKCHAIN));
  }
}
