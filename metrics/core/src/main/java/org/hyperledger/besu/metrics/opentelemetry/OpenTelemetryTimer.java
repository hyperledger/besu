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

import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.DoubleValueRecorder;

public class OpenTelemetryTimer implements LabelledMetric<OperationTimer> {

  private final DoubleValueRecorder recorder;
  private final String[] labelNames;

  public OpenTelemetryTimer(final DoubleValueRecorder recorder, final String... labelNames) {
    this.recorder = recorder;
    this.labelNames = labelNames;
  }

  @Override
  public OperationTimer labels(final String... labelValues) {
    List<String> labelKeysAndValues = new ArrayList<>();
    for (int i = 0; i < labelNames.length; i++) {
      labelKeysAndValues.add(labelNames[i]);
      labelKeysAndValues.add(labelValues[i]);
    }
    final Labels labels = Labels.of(labelKeysAndValues.toArray(new String[] {}));
    return () -> {
      final long startTime = System.nanoTime();
      return () -> {
        long elapsed = System.nanoTime() - startTime;
        recorder.record(elapsed, labels);
        return elapsed / 1e9;
      };
    };
  }
}
