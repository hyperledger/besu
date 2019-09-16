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
package org.hyperledger.besu.metrics.prometheus;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.BesuMetricCategory.NETWORK;
import static org.hyperledger.besu.metrics.BesuMetricCategory.PEERS;
import static org.hyperledger.besu.metrics.BesuMetricCategory.RPC;
import static org.hyperledger.besu.metrics.StandardMetricCategory.JVM;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Comparator;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class PrometheusMetricsSystemTest {

  private static final Comparator<Observation> IGNORE_VALUES =
      Comparator.<Observation, String>comparing(observation -> observation.getCategory().getName())
          .thenComparing(Observation::getMetricName)
          .thenComparing((o1, o2) -> o1.getLabels().equals(o2.getLabels()) ? 0 : 1);

  private final ObservableMetricsSystem metricsSystem =
      new PrometheusMetricsSystem(DEFAULT_METRIC_CATEGORIES, true);

  @Test
  public void shouldCreateObservationFromCounter() {
    final Counter counter = metricsSystem.createCounter(PEERS, "connected", "Some help string");

    counter.inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 1d, emptyList()));

    counter.inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 2d, emptyList()));
  }

  @Test
  public void shouldHandleDuplicateCounterCreation() {
    final LabelledMetric<Counter> counter1 =
        metricsSystem.createLabelledCounter(PEERS, "connected", "Some help string");
    final LabelledMetric<Counter> counter2 =
        metricsSystem.createLabelledCounter(PEERS, "connected", "Some help string");
    assertThat(counter1).isEqualTo(counter2);

    counter1.labels().inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 1d, emptyList()));

    counter2.labels().inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 2d, emptyList()));
  }

  @Test
  public void shouldCreateSeparateObservationsForEachCounterLabelValue() {
    final LabelledMetric<Counter> counter =
        metricsSystem.createLabelledCounter(PEERS, "connected", "Some help string", "labelName");

    counter.labels("value1").inc();
    counter.labels("value2").inc();
    counter.labels("value1").inc();

    assertThat(metricsSystem.streamObservations())
        .containsExactlyInAnyOrder(
            new Observation(PEERS, "connected", 2d, singletonList("value1")),
            new Observation(PEERS, "connected", 1d, singletonList("value2")));
  }

  @Test
  public void shouldIncrementCounterBySpecifiedAmount() {
    final Counter counter = metricsSystem.createCounter(PEERS, "connected", "Some help string");

    counter.inc(5);
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 5d, emptyList()));

    counter.inc(6);
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 11d, emptyList()));
  }

  @Test
  public void shouldCreateObservationsFromTimer() {
    final OperationTimer timer = metricsSystem.createTimer(RPC, "request", "Some help");

    final OperationTimer.TimingContext context = timer.startTimer();
    context.stopTimer();

    assertThat(metricsSystem.streamObservations())
        .usingElementComparator(IGNORE_VALUES)
        .containsExactlyInAnyOrder(
            new Observation(RPC, "request", null, asList("quantile", "0.2")),
            new Observation(RPC, "request", null, asList("quantile", "0.5")),
            new Observation(RPC, "request", null, asList("quantile", "0.8")),
            new Observation(RPC, "request", null, asList("quantile", "0.95")),
            new Observation(RPC, "request", null, asList("quantile", "0.99")),
            new Observation(RPC, "request", null, asList("quantile", "1.0")),
            new Observation(RPC, "request", null, singletonList("sum")),
            new Observation(RPC, "request", null, singletonList("count")));
  }

  @Test
  public void shouldHandleDuplicateTimerCreation() {
    final LabelledMetric<OperationTimer> timer1 =
        metricsSystem.createLabelledTimer(RPC, "request", "Some help");
    final LabelledMetric<OperationTimer> timer2 =
        metricsSystem.createLabelledTimer(RPC, "request", "Some help");
    assertThat(timer1).isEqualTo(timer2);
  }

  @Test
  public void shouldCreateObservationsFromTimerWithLabels() {
    final LabelledMetric<OperationTimer> timer =
        metricsSystem.createLabelledTimer(RPC, "request", "Some help", "methodName");

    //noinspection EmptyTryBlock
    try (final OperationTimer.TimingContext ignored = timer.labels("method").startTimer()) {}

    assertThat(metricsSystem.streamObservations())
        .usingElementComparator(IGNORE_VALUES) // We don't know how long it will actually take.
        .containsExactlyInAnyOrder(
            new Observation(RPC, "request", null, asList("method", "quantile", "0.2")),
            new Observation(RPC, "request", null, asList("method", "quantile", "0.5")),
            new Observation(RPC, "request", null, asList("method", "quantile", "0.8")),
            new Observation(RPC, "request", null, asList("method", "quantile", "0.95")),
            new Observation(RPC, "request", null, asList("method", "quantile", "0.99")),
            new Observation(RPC, "request", null, asList("method", "quantile", "1.0")),
            new Observation(RPC, "request", null, asList("method", "sum")),
            new Observation(RPC, "request", null, asList("method", "count")));
  }

  @Test
  public void shouldNotCreateObservationsFromTimerWhenTimersDisabled() {
    final ObservableMetricsSystem metricsSystem =
        new PrometheusMetricsSystem(DEFAULT_METRIC_CATEGORIES, false);
    final LabelledMetric<OperationTimer> timer =
        metricsSystem.createLabelledTimer(RPC, "request", "Some help", "methodName");

    //noinspection EmptyTryBlock
    try (final OperationTimer.TimingContext ignored = timer.labels("method").startTimer()) {}

    assertThat(metricsSystem.streamObservations()).isEmpty();
  }

  @Test
  public void shouldCreateObservationFromGauge() {
    metricsSystem.createGauge(JVM, "myValue", "Help", () -> 7d);

    assertThat(metricsSystem.streamObservations())
        .containsExactlyInAnyOrder(new Observation(JVM, "myValue", 7d, emptyList()));
  }

  @Test
  public void shouldNotAllowDuplicateGaugeCreation() {
    // Gauges have a reference to the source of their data so creating it twice will still only
    // pull data from the first instance, possibly leaking memory and likely returning the wrong
    // results.
    metricsSystem.createGauge(JVM, "myValue", "Help", () -> 7d);
    assertThatThrownBy(() -> metricsSystem.createGauge(JVM, "myValue", "Help", () -> 7d))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldOnlyObserveEnabledMetrics() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .metricCategories(ImmutableSet.of(BesuMetricCategory.RPC))
            .enabled(true)
            .build();
    final ObservableMetricsSystem localMetricSystem =
        PrometheusMetricsSystem.init(metricsConfiguration);

    // do a category we are not watching
    final LabelledMetric<Counter> counterN =
        localMetricSystem.createLabelledCounter(NETWORK, "ABC", "Not that kind of network", "show");
    assertThat(counterN).isSameAs(NoOpMetricsSystem.NO_OP_LABELLED_1_COUNTER);

    counterN.labels("show").inc();
    assertThat(localMetricSystem.streamObservations()).isEmpty();

    // do a category we are watching
    final LabelledMetric<Counter> counterR =
        localMetricSystem.createLabelledCounter(RPC, "name", "Not useful", "method");
    assertThat(counterR).isNotSameAs(NoOpMetricsSystem.NO_OP_LABELLED_1_COUNTER);

    counterR.labels("op").inc();
    assertThat(localMetricSystem.streamObservations())
        .containsExactly(new Observation(RPC, "name", 1.0, singletonList("op")));
  }

  @Test
  public void returnsNoOpMetricsWhenAllDisabled() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder().enabled(false).pushEnabled(false).build();
    final MetricsSystem localMetricSystem = PrometheusMetricsSystem.init(metricsConfiguration);

    assertThat(localMetricSystem).isInstanceOf(NoOpMetricsSystem.class);
  }

  @Test
  public void returnsPrometheusMetricsWhenEnabled() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder().enabled(true).pushEnabled(false).build();
    final MetricsSystem localMetricSystem = PrometheusMetricsSystem.init(metricsConfiguration);

    assertThat(localMetricSystem).isInstanceOf(PrometheusMetricsSystem.class);
  }

  @Test
  public void returnsNoOpMetricsWhenPushEnabled() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder().enabled(false).pushEnabled(true).build();
    final MetricsSystem localMetricSystem = PrometheusMetricsSystem.init(metricsConfiguration);

    assertThat(localMetricSystem).isInstanceOf(PrometheusMetricsSystem.class);
  }
}
