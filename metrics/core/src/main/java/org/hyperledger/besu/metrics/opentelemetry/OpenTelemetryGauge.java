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

import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

import com.google.common.base.Preconditions;
import io.opentelemetry.api.metrics.AsynchronousInstrument;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.metrics.common.LabelsBuilder;

public class OpenTelemetryGauge implements LabelledGauge {
  private final List<String> labelNames;
  private final Map<Labels, DoubleSupplier> observationsMap = new ConcurrentHashMap<>();

  public OpenTelemetryGauge(
      final String metricName,
      final String help,
      final Meter meter,
      final List<String> labelNames) {
    this.labelNames = labelNames;

    meter
        .doubleValueObserverBuilder(metricName)
        .setDescription(help)
        .setUpdater(this::updater)
        .build();
  }

  @Override
  public void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
    Preconditions.checkArgument(
        labelValues.length == labelNames.size(),
        "label values and label names need the same number of elements");
    final Labels labels = getLabels(labelValues);
    if (observationsMap.putIfAbsent(labels, valueSupplier) != null) {
      throw new IllegalStateException(
          "Already registered a gauge with labels " + Arrays.toString(labelValues));
    }
  }

  private Labels getLabels(final String... labelValues) {
    final LabelsBuilder labelsBuilder = Labels.builder();
    for (int i = 0; i < labelNames.size(); i++) {
      labelsBuilder.put(labelNames.get(i), labelValues[i]);
    }
    return labelsBuilder.build();
  }

  private void updater(final AsynchronousInstrument.DoubleResult doubleResult) {
    observationsMap.forEach(
        (labels, valueSupplier) -> doubleResult.observe(valueSupplier.getAsDouble(), labels));
  }
}
