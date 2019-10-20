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
package org.hyperledger.besu.cli.converter;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import picocli.CommandLine;

public class MetricCategoryConverter implements CommandLine.ITypeConverter<MetricCategory> {

  private final Map<String, MetricCategory> metricCategories = new HashMap<>();

  @Override
  public MetricCategory convert(final String value) {
    final MetricCategory category = metricCategories.get(value);
    if (category == null) {
      throw new IllegalArgumentException("Unknown category: " + value);
    }
    return category;
  }

  public <T extends Enum<T> & MetricCategory> void addCategories(final Class<T> categoryEnum) {
    EnumSet.allOf(categoryEnum)
        .forEach(category -> metricCategories.put(category.name(), category));
  }

  public void addRegistryCategory(final MetricCategory metricCategory) {
    metricCategories.put(metricCategory.getName().toUpperCase(), metricCategory);
  }

  @VisibleForTesting
  Map<String, MetricCategory> getMetricCategories() {
    return metricCategories;
  }
}
