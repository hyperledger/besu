/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.metrics;

import java.util.function.Supplier;
import java.util.stream.Stream;

public interface MetricsSystem {

  default Counter createCounter(
      final MetricCategory category, final String name, final String help) {
    return createLabelledCounter(category, name, help, new String[0]).labels();
  }

  LabelledMetric<Counter> createLabelledCounter(
      MetricCategory category, String name, String help, String... labelNames);

  default OperationTimer createTimer(
      final MetricCategory category, final String name, final String help) {
    return createLabelledTimer(category, name, help, new String[0]).labels();
  }

  LabelledMetric<OperationTimer> createLabelledTimer(
      MetricCategory category, String name, String help, String... labelNames);

  void createGauge(
      MetricCategory category, String name, String help, Supplier<Double> valueSupplier);

  default void createIntegerGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<Integer> valueSupplier) {
    createGauge(category, name, help, () -> (double) valueSupplier.get());
  }

  default void createLongGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<Long> valueSupplier) {
    createGauge(category, name, help, () -> (double) valueSupplier.get());
  }

  Stream<Observation> getMetrics(MetricCategory category);

  default Stream<Observation> getMetrics() {
    return Stream.of(MetricCategory.values()).flatMap(this::getMetrics);
  }
}
