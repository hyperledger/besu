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

import tech.pegasys.pantheon.metrics.ObservableMetricsSystem;
import tech.pegasys.pantheon.metrics.Observation;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.metrics.LabelledMetric;
import tech.pegasys.pantheon.plugin.services.metrics.MetricCategory;
import tech.pegasys.pantheon.plugin.services.metrics.OperationTimer;

import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;

public class NoOpMetricsSystem implements ObservableMetricsSystem {

  public static final Counter NO_OP_COUNTER = new NoOpCounter();
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
  public Stream<Observation> streamObservations(final MetricCategory category) {
    return Stream.empty();
  }

  @Override
  public Stream<Observation> streamObservations() {
    return Stream.empty();
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
}
