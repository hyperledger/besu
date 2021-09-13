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

import static java.util.Arrays.asList;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

public class StubMetricsSystem implements ObservableMetricsSystem {

  private final Map<String, StubLabelledCounter> counters = new HashMap<>();
  private final Map<String, DoubleSupplier> gauges = new HashMap<>();

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return counters.computeIfAbsent(name, key -> new StubLabelledCounter());
  }

  @Override
  public LabelledGauge createLabelledGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return NoOpMetricsSystem.getLabelledGauge(labelNames.length);
  }

  public long getCounterValue(final String name, final String... labels) {
    final StubLabelledCounter labelledCounter = counters.get(name);
    if (labelledCounter == null) {
      throw new IllegalArgumentException("Unknown counter: " + name);
    }
    final StubCounter metric = labelledCounter.getMetric(labels);
    if (metric == null) {
      return 0;
    }
    return metric.getValue();
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return labelValues -> NoOpMetricsSystem.NO_OP_OPERATION_TIMER;
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {
    gauges.put(name, valueSupplier);
  }

  public double getGaugeValue(final String name) {
    final DoubleSupplier gauge = gauges.get(name);
    if (gauge == null) {
      throw new IllegalArgumentException("Unknown gauge: " + name);
    }
    return gauge.getAsDouble();
  }

  @Override
  public Stream<Observation> streamObservations(final MetricCategory category) {
    throw new UnsupportedOperationException("Observations aren't actually recorded");
  }

  @Override
  public Stream<Observation> streamObservations() {
    throw new UnsupportedOperationException("Observations aren't actually recorded");
  }

  @Override
  public Set<MetricCategory> getEnabledCategories() {
    return Collections.emptySet();
  }

  public static class StubLabelledCounter implements LabelledMetric<Counter> {
    private final Map<List<String>, StubCounter> metrics = new HashMap<>();

    @Override
    public Counter labels(final String... labels) {
      return metrics.computeIfAbsent(asList(labels), key -> new StubCounter());
    }

    private StubCounter getMetric(final String... labels) {
      return metrics.get(asList(labels));
    }
  }

  public static class StubCounter implements Counter {
    private long value = 0;

    @Override
    public void inc() {
      value++;
    }

    @Override
    public void inc(final long amount) {
      value += amount;
    }

    public long getValue() {
      return value;
    }
  }
}
