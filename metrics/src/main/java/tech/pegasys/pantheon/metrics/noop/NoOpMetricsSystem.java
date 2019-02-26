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
package tech.pegasys.pantheon.metrics.noop;

import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.Observation;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.metrics.OperationTimer.TimingContext;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.prometheus.client.Collector;

public class NoOpMetricsSystem implements MetricsSystem {

  private static final Counter NO_OP_COUNTER = new NoOpCounter();
  private static final TimingContext NO_OP_TIMING_CONTEXT = () -> 0;
  private static final OperationTimer NO_OP_TIMER = () -> NO_OP_TIMING_CONTEXT;
  public static final LabelledMetric<OperationTimer> NO_OP_LABELLED_TIMER = label -> NO_OP_TIMER;
  public static final LabelledMetric<Counter> NO_OP_LABELLED_COUNTER = label -> NO_OP_COUNTER;
  public static final Collector NO_OP_COLLECTOR =
      new Collector() {
        @Override
        public List<MetricFamilySamples> collect() {
          return Collections.emptyList();
        }
      };

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return NO_OP_LABELLED_COUNTER;
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return NO_OP_LABELLED_TIMER;
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<Double> valueSupplier) {}

  @Override
  public Stream<Observation> getMetrics(final MetricCategory category) {
    return Stream.empty();
  }

  @Override
  public Stream<Observation> getMetrics() {
    return Stream.empty();
  }
}
