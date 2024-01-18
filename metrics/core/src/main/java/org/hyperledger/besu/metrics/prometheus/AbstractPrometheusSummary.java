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
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.ArrayList;
import java.util.stream.Stream;

import io.prometheus.metrics.model.snapshots.SummarySnapshot;

abstract class AbstractPrometheusSummary extends CategorizedPrometheusCollector {

  /**
   * Create a new collector assigned to the given category and with the given name, and computed the
   * prefixed name.
   *
   * @param category The {@link MetricCategory} this collector is assigned to
   * @param name The name of this collector
   */
  protected AbstractPrometheusSummary(final MetricCategory category, final String name) {
    super(category, name);
  }

  protected abstract SummarySnapshot collect();

  @Override
  public Stream<Observation> streamObservations() {
    return collect().getDataPoints().stream()
        .flatMap(
            dataPoint -> {
              final var labelValues = PrometheusCollector.getLabelValues(dataPoint.getLabels());
              final var quantiles = dataPoint.getQuantiles();
              final var observations = new ArrayList<Observation>(quantiles.size() + 2);

              if (dataPoint.hasSum()) {
                observations.add(
                    new Observation(
                        category,
                        name,
                        dataPoint.getSum(),
                        PrometheusCollector.addLabelValues(labelValues, "sum")));
              }

              if (dataPoint.hasCount()) {
                observations.add(
                    new Observation(
                        category,
                        name,
                        dataPoint.getCount(),
                        PrometheusCollector.addLabelValues(labelValues, "count")));
              }

              quantiles.forEach(
                  quantile ->
                      observations.add(
                          new Observation(
                              category,
                              name,
                              quantile.getValue(),
                              PrometheusCollector.addLabelValues(
                                  labelValues,
                                  "quantile",
                                  Double.toString(quantile.getQuantile())))));

              return observations.stream();
            });
  }
}
