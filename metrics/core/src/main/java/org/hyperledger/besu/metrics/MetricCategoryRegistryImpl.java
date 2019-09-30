/*
 * Copyright 2019 ConsenSys AG.
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

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;

import java.util.ArrayList;
import java.util.List;

public class MetricCategoryRegistryImpl implements MetricCategoryRegistry {

  private final List<MetricCategory> metricCategories = new ArrayList<>();

  public List<MetricCategory> getMetricCategories() {
    return metricCategories;
  }

  @Override
  public void addMetricCategory(final MetricCategory newMetricCategory) {
    metricCategories.add(newMetricCategory);
  }
}
