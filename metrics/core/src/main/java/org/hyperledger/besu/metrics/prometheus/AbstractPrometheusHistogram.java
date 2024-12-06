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

import static org.hyperledger.besu.metrics.prometheus.PrometheusCollector.addLabelValues;
import static org.hyperledger.besu.metrics.prometheus.PrometheusCollector.getLabelValues;

import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.ArrayList;
import java.util.stream.Stream;

import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

/**
 * Abstract base class for Prometheus histogram collectors. A histogram samples durations and counts
 * them in configurable buckets. * It also provides a sum of all observed values.
 */
abstract class AbstractPrometheusHistogram extends CategorizedPrometheusCollector {
  protected Histogram histogram;

  protected AbstractPrometheusHistogram(
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
  public String getIdentifier() {
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
              if (!dataPoint.hasClassicHistogramData()) {
                throw new IllegalStateException("Only classic histogram are supported");
              }

              final var labelValues = getLabelValues(dataPoint.getLabels());
              final var classicBuckets = dataPoint.getClassicBuckets();
              final var observations = new ArrayList<Observation>(classicBuckets.size() + 2);

              if (dataPoint.hasSum()) {
                observations.add(
                    new Observation(
                        category, name, dataPoint.getSum(), addLabelValues(labelValues, "sum")));
              }

              if (dataPoint.hasCount()) {
                observations.add(
                    new Observation(
                        category,
                        name,
                        dataPoint.getCount(),
                        addLabelValues(labelValues, "count")));
              }

              classicBuckets.stream()
                  .forEach(
                      bucket ->
                          observations.add(
                              new Observation(
                                  category,
                                  name,
                                  bucket.getCount(),
                                  addLabelValues(
                                      labelValues,
                                      "bucket",
                                      Double.toString(bucket.getUpperBound())))));

              return observations.stream();
            });
  }
}
