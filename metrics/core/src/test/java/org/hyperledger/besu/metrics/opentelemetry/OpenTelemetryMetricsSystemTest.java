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
package org.hyperledger.besu.metrics.opentelemetry;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.BesuMetricCategory.DEFAULT_METRIC_CATEGORIES;
import static org.hyperledger.besu.metrics.BesuMetricCategory.NETWORK;
import static org.hyperledger.besu.metrics.BesuMetricCategory.PEERS;
import static org.hyperledger.besu.metrics.BesuMetricCategory.RPC;
import static org.hyperledger.besu.metrics.MetricsProtocol.OPENTELEMETRY;

import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.MetricsSystemFactory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class OpenTelemetryMetricsSystemTest {

  private static final Comparator<Observation> IGNORE_VALUES =
      Comparator.<Observation, String>comparing(observation -> observation.getCategory().getName())
          .thenComparing(Observation::getMetricName)
          .thenComparing((o1, o2) -> o1.getLabels().equals(o2.getLabels()) ? 0 : 1);

  private final ObservableMetricsSystem metricsSystem =
      new OpenTelemetrySystem(DEFAULT_METRIC_CATEGORIES, true, "job");

  @Test
  public void shouldCreateObservationFromCounter() {
    final Counter counter = metricsSystem.createCounter(PEERS, "connected", "Some help string");

    counter.inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 1L, emptyList()));

    counter.inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 2L, emptyList()));
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
        .containsExactly(new Observation(PEERS, "connected", 1L, emptyList()));

    counter2.labels().inc();
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 2L, emptyList()));
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
            new Observation(PEERS, "connected", 2L, singletonList("value1")),
            new Observation(PEERS, "connected", 1L, singletonList("value2")));
  }

  @Test
  public void shouldIncrementCounterBySpecifiedAmount() {
    final Counter counter = metricsSystem.createCounter(PEERS, "connected", "Some help string");

    counter.inc(5);
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 5L, emptyList()));

    counter.inc(6);
    assertThat(metricsSystem.streamObservations())
        .containsExactly(new Observation(PEERS, "connected", 11L, emptyList()));
  }

  @Test
  public void shouldCreateObservationsFromTimer() {
    final OperationTimer timer = metricsSystem.createTimer(RPC, "request", "Some help");

    final OperationTimer.TimingContext context = timer.startTimer();
    context.stopTimer();

    assertThat(metricsSystem.streamObservations())
        .usingElementComparator(IGNORE_VALUES)
        .containsExactlyInAnyOrder(new Observation(RPC, "request", null, Collections.emptyList()));
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
    //noinspection EmptyTryBlock
    try (final OperationTimer.TimingContext ignored = timer.labels("method").startTimer()) {}
    //noinspection EmptyTryBlock
    try (final OperationTimer.TimingContext ignored = timer.labels("method").startTimer()) {}
    //noinspection EmptyTryBlock
    try (final OperationTimer.TimingContext ignored = timer.labels("method").startTimer()) {}

    assertThat(metricsSystem.streamObservations())
        .usingElementComparator(IGNORE_VALUES) // We don't know how long it will actually take.
        .containsExactlyInAnyOrder(new Observation(RPC, "request", null, singletonList("method")));
  }

  @Test
  public void shouldNotCreateObservationsFromTimerWhenTimersDisabled() {
    final ObservableMetricsSystem metricsSystem =
        new OpenTelemetrySystem(DEFAULT_METRIC_CATEGORIES, false, "job");
    final LabelledMetric<OperationTimer> timer =
        metricsSystem.createLabelledTimer(RPC, "request", "Some help", "methodName");

    //noinspection EmptyTryBlock
    try (final OperationTimer.TimingContext ignored = timer.labels("method").startTimer()) {}

    assertThat(metricsSystem.streamObservations()).isEmpty();
  }

  @Test
  public void shouldCreateObservationFromGauge() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .metricCategories(ImmutableSet.of(BesuMetricCategory.RPC))
            .enabled(true)
            .protocol(OPENTELEMETRY)
            .build();
    final ObservableMetricsSystem localMetricSystem =
        MetricsSystemFactory.create(metricsConfiguration);
    localMetricSystem.createGauge(RPC, "myValue", "Help", () -> 7.0);

    assertThat(localMetricSystem.streamObservations())
        .containsExactlyInAnyOrder(new Observation(RPC, "myValue", 7.0, emptyList()));
  }

  @Test
  public void shouldCreateLabelledGauge() {
    LabelledGauge labelledGauge =
        metricsSystem.createLabelledGauge(RPC, "gaugeName", "help", "a", "b");
    labelledGauge.labels(() -> 1.0, "a1", "b1");
    labelledGauge.labels(() -> 11.0, "a2", "b2");
    labelledGauge.labels(() -> 21.0, "a3", "b3");

    assertThat(metricsSystem.streamObservations())
        .containsExactlyInAnyOrder(
            new Observation(RPC, "gaugeName", 1.0, List.of("a1", "b1")),
            new Observation(RPC, "gaugeName", 11.0, List.of("a2", "b2")),
            new Observation(RPC, "gaugeName", 21.0, List.of("a3", "b3")));
  }

  @Test
  public void shouldOnlyObserveEnabledMetrics() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .metricCategories(ImmutableSet.of(BesuMetricCategory.RPC))
            .enabled(true)
            .protocol(OPENTELEMETRY)
            .build();
    final ObservableMetricsSystem localMetricSystem =
        MetricsSystemFactory.create(metricsConfiguration);

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
        .containsExactly(new Observation(RPC, "name", (long) 1, singletonList("op")));
  }

  @Test
  public void returnsNoOpMetricsWhenAllDisabled() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(false)
            .pushEnabled(false)
            .protocol(OPENTELEMETRY)
            .build();
    final MetricsSystem localMetricSystem = MetricsSystemFactory.create(metricsConfiguration);

    assertThat(localMetricSystem).isInstanceOf(NoOpMetricsSystem.class);
  }

  @Test
  public void returnsPrometheusMetricsWhenEnabled() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(true)
            .pushEnabled(false)
            .protocol(OPENTELEMETRY)
            .build();
    final MetricsSystem localMetricSystem = MetricsSystemFactory.create(metricsConfiguration);

    assertThat(localMetricSystem).isInstanceOf(OpenTelemetrySystem.class);
  }

  @Test
  public void returnsNoOpMetricsWhenPushEnabled() {
    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder()
            .enabled(false)
            .pushEnabled(true)
            .protocol(OPENTELEMETRY)
            .build();
    final MetricsSystem localMetricSystem = MetricsSystemFactory.create(metricsConfiguration);

    assertThat(localMetricSystem).isInstanceOf(OpenTelemetrySystem.class);
  }
}
