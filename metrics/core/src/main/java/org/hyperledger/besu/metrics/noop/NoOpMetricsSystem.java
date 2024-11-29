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
import org.hyperledger.besu.plugin.services.metrics.ExternalSummary;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedSummary;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;

/** The NoOp metrics system. */
public class NoOpMetricsSystem implements ObservableMetricsSystem {

  /** The constant NO_OP_COUNTER. */
  public static final Counter NO_OP_COUNTER = new NoOpCounter();

  private static final OperationTimer.TimingContext NO_OP_TIMING_CONTEXT = () -> 0;

  /** The constant NO_OP_OPERATION_TIMER. */
  public static final OperationTimer NO_OP_OPERATION_TIMER = () -> NO_OP_TIMING_CONTEXT;

  /** The constant NO_OP_LABELLED_1_COUNTER. */
  public static final LabelledMetric<Counter> NO_OP_LABELLED_1_COUNTER =
      new LabelCountingNoOpMetric<>(1, NO_OP_COUNTER);

  /** The constant NO_OP_LABELLED_2_COUNTER. */
  public static final LabelledMetric<Counter> NO_OP_LABELLED_2_COUNTER =
      new LabelCountingNoOpMetric<>(2, NO_OP_COUNTER);

  /** The constant NO_OP_LABELLED_3_COUNTER. */
  public static final LabelledMetric<Counter> NO_OP_LABELLED_3_COUNTER =
      new LabelCountingNoOpMetric<>(3, NO_OP_COUNTER);

  /** The constant NO_OP_LABELLED_1_OPERATION_TIMER. */
  public static final LabelledMetric<OperationTimer> NO_OP_LABELLED_1_OPERATION_TIMER =
      new LabelCountingNoOpMetric<>(1, NO_OP_OPERATION_TIMER);

  /** The constant NO_OP_HISTOGRAM. */
  public static final Histogram NO_OP_HISTOGRAM = d -> {};

  /** Default constructor */
  public NoOpMetricsSystem() {}

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getCounterLabelledMetric(labelNames.length);
  }

  /**
   * Gets counter labelled metric.
   *
   * @param labelCount the label count
   * @return the counter labelled metric
   */
  public static LabelledMetric<Counter> getCounterLabelledMetric(final int labelCount) {
    return new LabelCountingNoOpMetric<>(labelCount, NO_OP_COUNTER);
  }

  @Override
  public LabelledMetric<OperationTimer> createSimpleLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getOperationTimerLabelledMetric(labelNames.length);
  }

  @Override
  public LabelledSuppliedSummary createLabelledSuppliedSummary(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getLabelledSuppliedSummary(labelNames.length);
  }

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getOperationTimerLabelledMetric(labelNames.length);
  }

  /**
   * Gets operation timer labelled metric.
   *
   * @param labelCount the label count
   * @return the operation timer labelled metric
   */
  public static LabelledMetric<OperationTimer> getOperationTimerLabelledMetric(
      final int labelCount) {
    return new LabelCountingNoOpMetric<>(labelCount, NO_OP_OPERATION_TIMER);
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {}

  @Override
  public LabelledMetric<Histogram> createLabelledHistogram(
      final MetricCategory category,
      final String name,
      final String help,
      final double[] buckets,
      final String... labelNames) {
    return getHistogramLabelledMetric(labelNames.length);
  }

  /**
   * Gets histogram labelled metric.
   *
   * @param labelCount the label count
   * @return the histogram labelled metric
   */
  public static LabelledMetric<Histogram> getHistogramLabelledMetric(final int labelCount) {
    return new LabelCountingNoOpMetric<>(labelCount, NO_OP_HISTOGRAM);
  }

  @Override
  public void createGuavaCacheCollector(
      final MetricCategory category, final String name, final Cache<?, ?> cache) {}

  @Override
  public LabelledSuppliedMetric createLabelledSuppliedCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getLabelledSuppliedMetric(labelNames.length);
  }

  @Override
  public LabelledSuppliedMetric createLabelledSuppliedGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return getLabelledSuppliedMetric(labelNames.length);
  }

  /**
   * Gets labelled gauge.
   *
   * @param labelCount the label count
   * @return the labelled gauge
   */
  public static LabelledSuppliedMetric getLabelledSuppliedMetric(final int labelCount) {
    return new LabelledSuppliedNoOpMetric(labelCount);
  }

  /**
   * Gets labelled supplied histogram.
   *
   * @param labelCount the label count
   * @return the labelled gauge
   */
  public static LabelledSuppliedSummary getLabelledSuppliedSummary(final int labelCount) {
    return new LabelledSuppliedNoOpMetric(labelCount);
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

  @Override
  public void shutdown() {}

  /**
   * The Label counting NoOp metric.
   *
   * @param <T> the type parameter
   */
  public static class LabelCountingNoOpMetric<T> implements LabelledMetric<T> {

    /** The Label count. */
    final int labelCount;

    /** The Fake metric. */
    final T fakeMetric;

    /**
     * Instantiates a new Label counting NoOp metric.
     *
     * @param labelCount the label count
     * @param fakeMetric the fake metric
     */
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

  /** The Labelled supplied NoOp metric. */
  @SuppressWarnings("removal") // remove when deprecated LabelledGauge is removed
  public static class LabelledSuppliedNoOpMetric
      implements LabelledSuppliedMetric, LabelledGauge, LabelledSuppliedSummary {
    /** The Label count. */
    final int labelCount;

    /** The Label values cache. */
    final List<String> labelValuesCache = new ArrayList<>();

    /**
     * Instantiates a new Labelled supplied NoOp metric.
     *
     * @param labelCount the label count
     */
    public LabelledSuppliedNoOpMetric(final int labelCount) {
      this.labelCount = labelCount;
    }

    @Override
    public void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
      internalLabels(valueSupplier, labelValues);
    }

    @Override
    public void labels(
        final Supplier<ExternalSummary> summarySupplier, final String... labelValues) {
      internalLabels(summarySupplier, labelValues);
    }

    private void internalLabels(final Object valueSupplier, final String... labelValues) {
      final String labelValuesString = String.join(",", labelValues);
      Preconditions.checkArgument(
          !labelValuesCache.contains(labelValuesString),
          "Received label values that were already in use " + labelValuesString);
      Preconditions.checkArgument(
          labelValues.length == labelCount,
          "The count of labels used must match the count of labels expected.");
      Preconditions.checkNotNull(valueSupplier, "No valueSupplier specified");
      labelValuesCache.add(labelValuesString);
    }
  }
}
