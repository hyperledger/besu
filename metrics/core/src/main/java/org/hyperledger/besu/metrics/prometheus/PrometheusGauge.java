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
package org.hyperledger.besu.metrics.prometheus;

import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;

/** The Prometheus gauge. */
public class PrometheusGauge extends CategorizedPrometheusCollector implements LabelledGauge {
  private final GaugeWithCallback gauge;
  private final Map<List<String>, CallbackData> labelledCallbackData = new ConcurrentHashMap<>();

  /**
   * Instantiates a new labelled Prometheus gauge.
   *
   * @param category the {@link MetricCategory} this gauge is assigned to
   * @param name the metric name
   * @param help the help
   * @param labelNames the label names
   */
  public PrometheusGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    super(category, name);
    this.gauge =
        GaugeWithCallback.builder()
            .name(this.prefixedName)
            .help(help)
            .labelNames(labelNames)
            .callback(this::callback)
            .build();
  }

  /**
   * Instantiates a new unlabelled Prometheus gauge.
   *
   * @param category the {@link MetricCategory} this gauge is assigned to
   * @param name the metric name
   * @param help the help
   * @param valueSupplier the supplier of the value
   */
  public PrometheusGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {
    this(category, name, help);
    labelledCallbackData.put(List.of(), new CallbackData(valueSupplier, new String[0]));
  }

  private void callback(final GaugeWithCallback.Callback callback) {
    labelledCallbackData
        .values()
        .forEach(
            callbackData ->
                callback.call(callbackData.valueSupplier.getAsDouble(), callbackData.labelValues));
  }

  @Override
  public synchronized void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
    final var valueList = List.of(labelValues);
    if (labelledCallbackData.containsKey(valueList)) {
      throw new IllegalArgumentException(
          String.format("A gauge has already been created for label values %s", valueList));
    }

    labelledCallbackData.put(valueList, new CallbackData(valueSupplier, labelValues));
  }

  @Override
  public String getName() {
    return gauge.getPrometheusName();
  }

  @Override
  public void register(final PrometheusRegistry registry) {
    registry.register(gauge);
  }

  @Override
  public void unregister(final PrometheusRegistry registry) {
    registry.unregister(gauge);
  }

  private Observation convertToObservation(final GaugeSnapshot.GaugeDataPointSnapshot sample) {
    final List<String> labelValues = PrometheusCollector.getLabelValues(sample.getLabels());

    return new Observation(category, name, sample.getValue(), labelValues);
  }

  @Override
  public Stream<Observation> streamObservations() {
    final var snapshot = gauge.collect();
    return snapshot.getDataPoints().stream().map(this::convertToObservation);
  }

  private record CallbackData(DoubleSupplier valueSupplier, String[] labelValues) {}
}
