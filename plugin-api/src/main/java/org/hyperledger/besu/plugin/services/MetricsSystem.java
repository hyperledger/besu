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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** An interface for creating various Metrics components. */
public interface MetricsSystem extends BesuService {

  /**
   * Creates a Counter.
   *
   * @param category The {@link MetricCategory} this counter is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @return The created Counter instance.
   */
  default Counter createCounter(
      final MetricCategory category, final String name, final String help) {
    return createLabelledCounter(category, name, help, new String[0]).labels();
  }

  /**
   * Creates a Counter with assigned labels.
   *
   * @param category The {@link MetricCategory} this counter is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @param labelNames An array of labels to assign to the Counter.
   * @return The created LabelledMetric instance.
   */
  LabelledMetric<Counter> createLabelledCounter(
      MetricCategory category, String name, String help, String... labelNames);

  /**
   * Creates a Gauge with assigned labels.
   *
   * @param category The {@link MetricCategory} this gauge is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @param labelNames An array of labels to assign to the Gauge.
   * @return The created LabelledGauge instance.
   */
  LabelledGauge createLabelledGauge(
      MetricCategory category, String name, String help, String... labelNames);

  /**
   * Creates a Timer.
   *
   * @param category The {@link MetricCategory} this timer is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @return The created Timer instance.
   */
  default OperationTimer createTimer(
      final MetricCategory category, final String name, final String help) {
    return createLabelledTimer(category, name, help, new String[0]).labels();
  }

  /**
   * Creates a Timer with assigned labels.
   *
   * @param category The {@link MetricCategory} this timer is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @param labelNames An array of labels to assign to the Timer.
   * @return The created LabelledMetric instance.
   */
  LabelledMetric<OperationTimer> createLabelledTimer(
      MetricCategory category, String name, String help, String... labelNames);

  /**
   * Creates a gauge for displaying double vales. A gauge is a metric to report the current value.
   * The metric value may go up or down.
   *
   * @param category The {@link MetricCategory} this gauge is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @param valueSupplier A supplier for the double value to be presented.
   */
  void createGauge(MetricCategory category, String name, String help, DoubleSupplier valueSupplier);

  /**
   * Creates a gauge for displaying integer values.
   *
   * @param category The {@link MetricCategory} this gauge is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @param valueSupplier A supplier for the integer value to be presented.
   */
  default void createIntegerGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final IntSupplier valueSupplier) {
    createGauge(category, name, help, () -> (double) valueSupplier.getAsInt());
  }

  /**
   * Creates a gauge for displaying long values.
   *
   * @param category The {@link MetricCategory} this gauge is assigned to.
   * @param name A name for this metric.
   * @param help A human readable description of the metric.
   * @param valueSupplier A supplier for the long value to be presented.
   */
  default void createLongGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final LongSupplier valueSupplier) {
    createGauge(category, name, help, () -> (double) valueSupplier.getAsLong());
  }
}
