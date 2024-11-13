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

import static org.hyperledger.besu.metrics.prometheus.PrometheusCollector.getLabelValues;

import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import java.util.stream.Stream;

import io.prometheus.metrics.core.datapoints.CounterDataPoint;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

class PrometheusCounter extends CategorizedPrometheusCollector implements LabelledMetric<Counter> {
  private final io.prometheus.metrics.core.metrics.Counter counter;

  public PrometheusCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    super(category, name);
    this.counter =
        io.prometheus.metrics.core.metrics.Counter.builder()
            .name(this.prefixedName)
            .help(help)
            .labelNames(labelNames)
            .build();
  }

  @Override
  public Counter labels(final String... labels) {
    return new UnlabelledCounter(counter.labelValues(labels));
  }

  @Override
  public String getIdentifier() {
    return counter.getPrometheusName();
  }

  @Override
  public void register(final PrometheusRegistry registry) {
    registry.register(counter);
  }

  @Override
  public void unregister(final PrometheusRegistry registry) {
    registry.unregister(counter);
  }

  @Override
  public Stream<Observation> streamObservations() {
    return counter.collect().getDataPoints().stream()
        .map(
            sample ->
                new Observation(
                    category, name, sample.getValue(), getLabelValues(sample.getLabels())));
  }

  private record UnlabelledCounter(CounterDataPoint counter) implements Counter {

    @Override
    public void inc() {
      counter.inc();
    }

    @Override
    public void inc(final long amount) {
      counter.inc((double) amount);
    }
  }
}
