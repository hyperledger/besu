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
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.function.Supplier;

import io.prometheus.metrics.core.metrics.SummaryWithCallback;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.Quantile;
import io.prometheus.metrics.model.snapshots.Quantiles;
import io.prometheus.metrics.model.snapshots.SummarySnapshot;

class PrometheusExternalSummary extends AbstractPrometheusSummary {

  private final SummaryWithCallback summary;

  public PrometheusExternalSummary(
      final MetricCategory category,
      final String name,
      final String help,
      final Supplier<ExternalSummary> summarySupplier) {
    super(category, name);
    summary =
        SummaryWithCallback.builder()
            .name(name)
            .help(help)
            .callback(
                cb -> {
                  final var externalSummary = summarySupplier.get();
                  final var quantilesBuilder = Quantiles.builder();
                  externalSummary.quantiles().stream()
                      .map(pq -> new Quantile(pq.quantile(), pq.value()))
                      .forEach(quantilesBuilder::quantile);
                  cb.call(externalSummary.count(), externalSummary.sum(), quantilesBuilder.build());
                })
            .build();
  }

  @Override
  public String getIdentifier() {
    return summary.getPrometheusName();
  }

  @Override
  public void register(final PrometheusRegistry registry) {
    registry.register(summary);
  }

  @Override
  public void unregister(final PrometheusRegistry registry) {
    registry.unregister(summary);
  }

  @Override
  protected SummarySnapshot collect() {
    return summary.collect();
  }
}
