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
package org.hyperledger.besu.metrics.prometheus;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

/** A Prometheus collector that is assigned to a category */
public abstract class CategorizedPrometheusCollector implements PrometheusCollector {
  /** The {@link MetricCategory} this collector is assigned to */
  protected final MetricCategory category;

  /** The name of this collector */
  protected final String name;

  /** The prefixed name of this collector */
  protected final String prefixedName;

  /**
   * Create a new collector assigned to the given category and with the given name, and computed the
   * prefixed name.
   *
   * @param category The {@link MetricCategory} this collector is assigned to
   * @param name The name of this collector
   */
  protected CategorizedPrometheusCollector(final MetricCategory category, final String name) {
    this.category = category;
    this.name = name;
    this.prefixedName = prefixedName(category, name);
  }

  private static String categoryPrefix(final MetricCategory category) {
    return category.getApplicationPrefix().orElse("") + category.getName() + "_";
  }

  private static String prefixedName(final MetricCategory category, final String name) {
    return categoryPrefix(category) + name;
  }
}
