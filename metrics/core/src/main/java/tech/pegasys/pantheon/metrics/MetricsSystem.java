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

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

public interface MetricsSystem {

  default Counter createCounter(
      final PantheonMetricCategory category, final String name, final String help) {
    return createLabelledCounter(category, name, help, new String[0]).labels();
  }

  LabelledMetric<Counter> createLabelledCounter(
      PantheonMetricCategory category, String name, String help, String... labelNames);

  default OperationTimer createTimer(
      final PantheonMetricCategory category, final String name, final String help) {
    return createLabelledTimer(category, name, help, new String[0]).labels();
  }

  LabelledMetric<OperationTimer> createLabelledTimer(
      PantheonMetricCategory category, String name, String help, String... labelNames);

  void createGauge(
      PantheonMetricCategory category, String name, String help, DoubleSupplier valueSupplier);

  default void createIntegerGauge(
      final PantheonMetricCategory category,
      final String name,
      final String help,
      final IntSupplier valueSupplier) {
    createGauge(category, name, help, () -> (double) valueSupplier.getAsInt());
  }

  default void createLongGauge(
      final PantheonMetricCategory category,
      final String name,
      final String help,
      final LongSupplier valueSupplier) {
    createGauge(category, name, help, () -> (double) valueSupplier.getAsLong());
  }

  Stream<Observation> streamObservations(PantheonMetricCategory category);

  default Stream<Observation> streamObservations() {
    return Stream.of(PantheonMetricCategory.values()).flatMap(this::streamObservations);
  }
}
