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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.List;
import java.util.function.DoubleSupplier;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

class CurrentValueCollector extends Collector {

  private final String metricName;
  private final String help;
  private final DoubleSupplier valueSupplier;
  private final List<String> labelNames;
  private final List<String> labelValues;

  public CurrentValueCollector(
      final String metricName, final String help, final DoubleSupplier valueSupplier) {
    this(metricName, help, emptyList(), emptyList(), valueSupplier);
  }

  public CurrentValueCollector(
      final String metricName,
      final String help,
      final List<String> labelNames,
      final List<String> labelValues,
      final DoubleSupplier valueSupplier) {
    this.metricName = metricName;
    this.help = help;
    this.valueSupplier = valueSupplier;
    this.labelNames = labelNames;
    this.labelValues = labelValues;
  }

  @Override
  public List<MetricFamilySamples> collect() {
    final Sample sample =
        new Sample(metricName, labelNames, labelValues, valueSupplier.getAsDouble());
    return singletonList(
        new MetricFamilySamples(metricName, Type.GAUGE, help, singletonList(sample)));
  }
}
