/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.Set;
import java.util.stream.Stream;

public interface ObservableMetricsSystem extends MetricsSystem {

  Stream<Observation> streamObservations(MetricCategory category);

  Stream<Observation> streamObservations();

  /**
   * Provides an immutable view into the metric categories enabled for metric collection.
   *
   * @return the set of enabled metric categories.
   */
  Set<MetricCategory> getEnabledCategories();

  /**
   * Checks if a particular category of metrics is enabled.
   *
   * @param category the category to check
   * @return true if the category is enabled, false otherwise
   */
  default boolean isCategoryEnabled(final MetricCategory category) {
    return getEnabledCategories().stream()
        .anyMatch(metricCategory -> metricCategory.getName().equals(category.getName()));
  }
}
