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

import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

import io.prometheus.client.Collector;

/** The Prometheus supplied value collector. */
@SuppressWarnings("removal") // remove when deprecated LabelledGauge is removed
public class PrometheusSuppliedValueCollector extends Collector
    implements LabelledSuppliedMetric, LabelledGauge {
  private final Type type;
  private final String metricName;
  private final String help;
  private final List<String> labelNames;
  private final Map<List<String>, DoubleSupplier> observationsMap = new ConcurrentHashMap<>();

  /**
   * Instantiates a new Prometheus supplied value collector.
   *
   * @param type the type of the collector
   * @param metricName the metric name
   * @param help the help
   * @param labelNames the label names
   */
  public PrometheusSuppliedValueCollector(
      final Type type, final String metricName, final String help, final List<String> labelNames) {
    this.type = type;
    this.metricName = metricName;
    this.help = help;
    this.labelNames = labelNames;
  }

  @Override
  public synchronized void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
    validateLabelsCardinality(labelValues);
    if (observationsMap.putIfAbsent(List.of(labelValues), valueSupplier) != null) {
      final String labelValuesString = String.join(",", labelValues);
      throw new IllegalArgumentException(
          String.format("A gauge has already been created for label values %s", labelValuesString));
    }
  }

  @Override
  public List<MetricFamilySamples> collect() {
    final List<MetricFamilySamples.Sample> samples = new ArrayList<>();
    observationsMap.forEach(
        (labels, valueSupplier) ->
            samples.add(
                new MetricFamilySamples.Sample(
                    metricName, labelNames, labels, valueSupplier.getAsDouble())));
    return List.of(new MetricFamilySamples(metricName, type, help, samples));
  }

  private void validateLabelsCardinality(final String... labelValues) {
    if (labelValues.length != labelNames.size()) {
      throw new IllegalArgumentException(
          "Label values and label names must be the same cardinality");
    }
  }
}
