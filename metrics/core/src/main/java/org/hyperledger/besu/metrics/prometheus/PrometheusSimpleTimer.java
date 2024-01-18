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
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.stream.Stream;

import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

class PrometheusSimpleTimer extends CategorizedPrometheusCollector
    implements LabelledMetric<OperationTimer> {

  private final Histogram histogram;

  public PrometheusSimpleTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final double[] buckets,
      final String... labelNames) {
    super(category, name);
    this.histogram =
        Histogram.builder()
            .name(this.prefixedName)
            .help(help)
            .labelNames(labelNames)
            .classicOnly()
            .classicUpperBounds(buckets)
            .build();
  }

  @Override
  public OperationTimer labels(final String... labels) {
    final var ddp = histogram.labelValues(labels);
    return () -> ddp.startTimer()::observeDuration;
  }

  @Override
  public String getName() {
    return histogram.getPrometheusName();
  }

  @Override
  public void register(final PrometheusRegistry registry) {
    registry.register(histogram);
  }

  @Override
  public void unregister(final PrometheusRegistry registry) {
    registry.unregister(histogram);
  }

  @Override
  public Stream<Observation> streamObservations() {
    final var snapshot = histogram.collect();
    return snapshot.getDataPoints().stream()
        .flatMap(
            dataPoint -> {
              final var labelValues = PrometheusCollector.getLabelValues(dataPoint.getLabels());
              if (!dataPoint.hasClassicHistogramData()) {
                throw new IllegalStateException("Only classic histogram are supported");
              }

              return dataPoint.getClassicBuckets().stream()
                  .map(
                      bucket ->
                          new Observation(
                              category,
                              name,
                              bucket.getCount(),
                              PrometheusCollector.addLabelValues(
                                  labelValues, Double.toString(bucket.getUpperBound()))));
            });
  }
}
