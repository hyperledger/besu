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

import org.hyperledger.besu.plugin.services.metrics.ExternalSummary;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedSummary;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.prometheus.metrics.core.metrics.SummaryWithCallback;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;

/**
 * A Prometheus supplied summary collector. A supplied summary collector is one which actual value
 * is kept outside the metric system, for example in an external library or to calculate the value
 * only on demand when a metric scrape occurs.
 */
class PrometheusSuppliedSummary extends AbstractPrometheusSummary
    implements LabelledSuppliedSummary {
  /** Map label values with the collector callback data */
  protected final Map<List<String>, CallbackData> labelledCallbackData = new ConcurrentHashMap<>();

  public PrometheusSuppliedSummary(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    super(category, name);
    this.collector =
        SummaryWithCallback.builder()
            .name(name)
            .help(help)
            .labelNames(labelNames)
            .callback(this::callback)
            .build();
  }

  private void callback(final SummaryWithCallback.Callback callback) {
    labelledCallbackData
        .values()
        .forEach(
            callbackData -> {
              final var externalSummary = callbackData.summarySupplier().get();
              final var quantilesBuilder = Quantiles.builder();
              externalSummary.quantiles().stream()
                  .map(pq -> new Quantile(pq.quantile(), pq.value()))
                  .forEach(quantilesBuilder::quantile);
              callback.call(
                  externalSummary.count(), externalSummary.sum(), quantilesBuilder.build());
            });
  }

  @Override
  public synchronized void labels(
      final Supplier<ExternalSummary> summarySupplier, final String... labelValues) {
    final var valueList = List.of(labelValues);
    if (labelledCallbackData.containsKey(valueList)) {
      throw new IllegalArgumentException(
          String.format("A collector has already been created for label values %s", valueList));
    }

    labelledCallbackData.put(valueList, new CallbackData(summarySupplier, labelValues));
  }

  protected record CallbackData(Supplier<ExternalSummary> summarySupplier, String[] labelValues) {}
}
