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
package org.hyperledger.besu.metrics.noop;

import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;

public class NoOpMetricsSystem implements ObservableMetricsSystem {

  public static final Counter NO_OP_COUNTER = new NoOpCounter();
  public static final LabelledGauge NO_OP_GAUGE = new NoOpValueCollector();
  private static final OperationTimer.TimingContext NO_OP_TIMING_CONTEXT = () -> 0;
  public static final OperationTimer NO_OP_OPERATION_TIMER = () -> NO_OP_TIMING_CONTEXT;

  public static final LabelledMetric<Counter> NO_OP_LABELLED_1_COUNTER =
      new LabelCountingNoOpMetric<>(1, NO_OP_COUNTER);
  public static final LabelledMetric<Counter> NO_OP_LABELLED_2_COUNTER =
      new LabelCountingNoOpMetric<>(2, NO_OP_COUNTER);
  public static final LabelledMetric<Counter> NO_OP_LABELLED_3_COUNTER =
      new LabelCountingNoOpMetric<>(3, NO_OP_COUNTER);
  public static final LabelledMetric<OperationTimer> NO_OP_LABELLED_1_OPERATION_TIMER =
      new LabelCountingNoOpMetric<>(1, NO_OP_OPERATION_TIMER);
  public static final LabelledGauge NO_OP_LABELLED_1_GAUGE =
      new LabelledGaugeNoOpMetric(1, NO_OP_GAUGE);
  public static final LabelledGauge NO_OP_LABELLED_2_GAUGE =
      new LabelledGaugeNoOpMetric(2, NO_OP_GAUGE);
  public static final LabelledGauge NO_OP_LABELLED_3_GAUGE =
      new LabelledGaugeNoOpMetric(3, NO_OP_GAUGE);

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getCounterLabelledMetric(labelNames.length);
  }

  public static LabelledMetric<Counter> getCounterLabelledMetric(final int labelCount) {
    switch (labelCount) {
      case 1:
        return NO_OP_LABELLED_1_COUNTER;
      case 2:
        return NO_OP_LABELLED_2_COUNTER;
      case 3:
        return NO_OP_LABELLED_3_COUNTER;
      default:
        return new LabelCountingNoOpMetric<>(labelCount, NO_OP_COUNTER);
    }
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getOperationTimerLabelledMetric(labelNames.length);
  }

  public static LabelledMetric<OperationTimer> getOperationTimerLabelledMetric(
      final int labelCount) {
    if (labelCount == 1) {
      return NO_OP_LABELLED_1_OPERATION_TIMER;
    } else {
      return new LabelCountingNoOpMetric<>(labelCount, NO_OP_OPERATION_TIMER);
    }
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {}

  @Override
  public LabelledGauge createLabelledGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getLabelledGauge(labelNames.length);
  }

  public static LabelledGauge getLabelledGauge(final int labelCount) {
    switch (labelCount) {
      case 1:
        return NO_OP_LABELLED_1_GAUGE;
      case 2:
        return NO_OP_LABELLED_2_GAUGE;
      case 3:
        return NO_OP_LABELLED_3_GAUGE;
      default:
        return new LabelledGaugeNoOpMetric(labelCount, NO_OP_GAUGE);
    }
  }

  @Override
  public Stream<Observation> streamObservations(final MetricCategory category) {
    return Stream.empty();
  }

  @Override
  public Stream<Observation> streamObservations() {
    return Stream.empty();
  }

  @Override
  public Set<MetricCategory> getEnabledCategories() {
    return Collections.emptySet();
  }

  public static class LabelCountingNoOpMetric<T> implements LabelledMetric<T> {

    final int labelCount;
    final T fakeMetric;

    LabelCountingNoOpMetric(final int labelCount, final T fakeMetric) {
      this.labelCount = labelCount;
      this.fakeMetric = fakeMetric;
    }

    @Override
    public T labels(final String... labels) {
      Preconditions.checkArgument(
          labels.length == labelCount,
          "The count of labels used must match the count of labels expected.");
      return fakeMetric;
    }
  }

  public static class LabelledGaugeNoOpMetric implements LabelledGauge {
    final int labelCount;
    final List<String> labelValuesCache = new ArrayList<>();

    public LabelledGaugeNoOpMetric(final int labelCount, final LabelledGauge fakeMetric) {
      this.labelCount = labelCount;
      this.fakeMetric = fakeMetric;
    }

    final LabelledGauge fakeMetric;

    @Override
    public void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
      final String labelValuesString = String.join(",", labelValues);
      Preconditions.checkArgument(
          !labelValuesCache.contains(labelValuesString),
          "Received label values that were already in use " + labelValuesString);
      Preconditions.checkArgument(
          labelValues.length == labelCount,
          "The count of labels used must match the count of labels expected.");
      Preconditions.checkNotNull(valueSupplier, "No valueSupplier specified");
    }
  }
}
