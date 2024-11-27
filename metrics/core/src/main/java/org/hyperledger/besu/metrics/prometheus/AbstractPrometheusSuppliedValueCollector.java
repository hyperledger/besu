/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.DataPointSnapshot;

/**
 * Abstract base class for Prometheus supplied value collectors. A supplied value collector is one
 * which actual value is kept outside the metric system, for example in an external library or to
 * calculate the value only on demand when a metric scrape occurs. This class provides common
 * functionality for Prometheus supplied value collectors.
 */
abstract class AbstractPrometheusSuppliedValueCollector extends CategorizedPrometheusCollector
    implements LabelledSuppliedMetric {
  /** The collector */
  protected final Collector collector;

  /** Map label values with the collector callback data */
  protected final Map<List<String>, CallbackData> labelledCallbackData = new ConcurrentHashMap<>();

  protected AbstractPrometheusSuppliedValueCollector(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    super(category, name);
    this.collector = createCollector(help, labelNames);
  }

  protected abstract Collector createCollector(final String help, final String... labelNames);

  @Override
  public void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
    final var valueList = List.of(labelValues);
    if (labelledCallbackData.putIfAbsent(valueList, new CallbackData(valueSupplier, labelValues))
        != null) {
      throw new IllegalArgumentException(
          String.format("A collector has already been created for label values %s", valueList));
    }
  }

  @Override
  public String getIdentifier() {
    return collector.getPrometheusName();
  }

  @Override
  public void register(final PrometheusRegistry registry) {
    registry.register(collector);
  }

  @Override
  public void unregister(final PrometheusRegistry registry) {
    registry.unregister(collector);
  }

  @Override
  public Stream<Observation> streamObservations() {
    final var snapshot = collector.collect();
    return snapshot.getDataPoints().stream().map(this::convertToObservation);
  }

  /**
   * Convert the collected sample to an observation
   *
   * @param sample the collected sample
   * @return an observation
   */
  protected abstract Observation convertToObservation(final DataPointSnapshot sample);

  protected record CallbackData(DoubleSupplier valueSupplier, String[] labelValues) {}
}
