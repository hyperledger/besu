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
package tech.pegasys.pantheon.metrics.prometheus;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.metrics.MetricCategory.JVM;
import static tech.pegasys.pantheon.metrics.MetricCategory.NETWORK;
import static tech.pegasys.pantheon.metrics.MetricCategory.PEERS;
import static tech.pegasys.pantheon.metrics.MetricCategory.RPC;

import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.Observation;
import tech.pegasys.pantheon.metrics.OperationTimer;
import tech.pegasys.pantheon.metrics.OperationTimer.TimingContext;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.Comparator;
import java.util.EnumSet;

import org.junit.Test;

public class PrometheusMetricsSystemTest {

  private static final Comparator<Observation> IGNORE_VALUES =
      Comparator.comparing(Observation::getCategory)
          .thenComparing(Observation::getMetricName)
          .thenComparing((o1, o2) -> o1.getLabels().equals(o2.getLabels()) ? 0 : 1);

  private final MetricsSystem metricsSystem = new PrometheusMetricsSystem();

  @Test
  public void shouldCreateObservationFromCounter() {
    final Counter counter = metricsSystem.createCounter(PEERS, "connected", "Some help string");

    counter.inc();
    assertThat(metricsSystem.getMetrics())
        .containsExactly(new Observation(PEERS, "connected", 1d, emptyList()));

    counter.inc();
    assertThat(metricsSystem.getMetrics())
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
    assertThat(metricsSystem.getMetrics())
        .containsExactly(new Observation(PEERS, "connected", 1d, emptyList()));

    counter2.labels().inc();
    assertThat(metricsSystem.getMetrics())
        .containsExactly(new Observation(PEERS, "connected", 2d, emptyList()));
  }

  @Test
  public void shouldCreateSeparateObservationsForEachCounterLabelValue() {
    final LabelledMetric<Counter> counter =
        metricsSystem.createLabelledCounter(PEERS, "connected", "Some help string", "labelName");

    counter.labels("value1").inc();
    counter.labels("value2").inc();
    counter.labels("value1").inc();

    assertThat(metricsSystem.getMetrics())
        .containsExactlyInAnyOrder(
            new Observation(PEERS, "connected", 2d, singletonList("value1")),
            new Observation(PEERS, "connected", 1d, singletonList("value2")));
  }

  @Test
  public void shouldIncrementCounterBySpecifiedAmount() {
    final Counter counter = metricsSystem.createCounter(PEERS, "connected", "Some help string");

    counter.inc(5);
    assertThat(metricsSystem.getMetrics())
        .containsExactly(new Observation(PEERS, "connected", 5d, emptyList()));

    counter.inc(6);
    assertThat(metricsSystem.getMetrics())
        .containsExactly(new Observation(PEERS, "connected", 11d, emptyList()));
  }

  @Test
  public void shouldCreateObservationsFromTimer() {
    final OperationTimer timer = metricsSystem.createTimer(RPC, "request", "Some help");

    final TimingContext context = timer.startTimer();
    context.stopTimer();

    assertThat(metricsSystem.getMetrics())
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
    try (final TimingContext ignored = timer.labels("method").startTimer()) {}

    assertThat(metricsSystem.getMetrics())
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
  public void shouldCreateObservationFromGauge() {
    metricsSystem.createGauge(JVM, "myValue", "Help", () -> 7d);

    assertThat(metricsSystem.getMetrics())
        .containsExactlyInAnyOrder(new Observation(JVM, "myValue", 7d, emptyList()));
  }

  @Test
  public void shouldHandleDuplicateGaugeDeclaration() {
    metricsSystem.createGauge(JVM, "myValue", "Help", () -> 7d);
    metricsSystem.createGauge(JVM, "myValue", "Help", () -> 7d);

    assertThat(metricsSystem.getMetrics())
        .containsExactlyInAnyOrder(new Observation(JVM, "myValue", 7d, emptyList()));
  }

  @Test
  public void shouldOnlyObserveEnabledMetrics() {
    final MetricsConfiguration metricsConfiguration = MetricsConfiguration.createDefault();
    metricsConfiguration.setMetricCategories(EnumSet.of(MetricCategory.RPC));
    metricsConfiguration.setEnabled(true);
    final MetricsSystem localMetricSystem = PrometheusMetricsSystem.init(metricsConfiguration);

    // do a category we are not watching
    final LabelledMetric<Counter> counterN =
        localMetricSystem.createLabelledCounter(NETWORK, "ABC", "Not that kind of network", "show");
    assertThat(counterN).isSameAs(NoOpMetricsSystem.NO_OP_LABELLED_COUNTER);

    counterN.labels("show").inc();
    assertThat(localMetricSystem.getMetrics()).isEmpty();

    // do a category we are watching
    final LabelledMetric<Counter> counterR =
        localMetricSystem.createLabelledCounter(RPC, "name", "Not useful", "method");
    assertThat(counterR).isNotSameAs(NoOpMetricsSystem.NO_OP_LABELLED_COUNTER);

    counterR.labels("op").inc();
    assertThat(localMetricSystem.getMetrics())
        .containsExactly(new Observation(RPC, "name", 1.0, singletonList("op")));
  }
}
