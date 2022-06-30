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
package org.hyperledger.besu.metrics.opentelemetry;

import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;

public class OpenTelemetryTimer implements LabelledMetric<OperationTimer> {

  private final String help;
  private final Meter meter;
  private final String metricName;
  private final String[] labelNames;

  public OpenTelemetryTimer(
      final String metricName, final String help, final Meter meter, final String... labelNames) {
    this.metricName = metricName;
    this.help = help;
    this.meter = meter;
    this.labelNames = labelNames;
  }

  @Override
  public OperationTimer labels(final String... labelValues) {
    AttributesBuilder builder = Attributes.builder();
    for (int i = 0; i < labelNames.length; i++) {
      builder.put(labelNames[i], labelValues[i]);
    }
    final Attributes labels = builder.build();

    return () -> {
      final long startTime = System.nanoTime();
      return () -> {
        long elapsed = System.nanoTime() - startTime;
        meter
            .gaugeBuilder(metricName)
            .setDescription(help)
            .buildWithCallback((measurement) -> measurement.observe((double) elapsed, labels));
        return elapsed / 1e9;
      };
    };
  }
}
