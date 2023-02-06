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

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;

/** The Observation. */
public class Observation {
  private final MetricCategory category;
  private final String metricName;
  private final List<String> labels;
  private final Object value;

  /**
   * Instantiates a new Observation.
   *
   * @param category the category
   * @param metricName the metric name
   * @param value the value
   * @param labels the labels
   */
  public Observation(
      final MetricCategory category,
      final String metricName,
      final Object value,
      final List<String> labels) {
    this.category = category;
    this.metricName = metricName;
    this.value = value;
    this.labels = labels;
  }

  /**
   * Gets category.
   *
   * @return the category
   */
  public MetricCategory getCategory() {
    return category;
  }

  /**
   * Gets metric name.
   *
   * @return the metric name
   */
  public String getMetricName() {
    return metricName;
  }

  /**
   * Gets labels.
   *
   * @return the labels
   */
  public List<String> getLabels() {
    return labels;
  }

  /**
   * Gets value.
   *
   * @return the value
   */
  public Object getValue() {
    return value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Observation that = (Observation) o;
    return Objects.equals(category, that.category)
        && Objects.equals(metricName, that.metricName)
        && Objects.equals(labels, that.labels)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(category, metricName, labels, value);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("category", category)
        .add("metricName", metricName)
        .add("labels", labels)
        .add("value", value)
        .toString();
  }
}
