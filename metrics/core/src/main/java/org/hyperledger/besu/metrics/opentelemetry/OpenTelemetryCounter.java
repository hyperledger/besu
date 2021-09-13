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

import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.LongCounter;

public class OpenTelemetryCounter implements LabelledMetric<Counter> {

  private final LongCounter counter;
  private final String[] labelNames;

  public OpenTelemetryCounter(final LongCounter counter, final String... labelNames) {
    this.counter = counter;
    this.labelNames = labelNames;
  }

  @Override
  public Counter labels(final String... labelValues) {
    final AttributesBuilder builder = Attributes.builder();
    for (int i = 0; i < labelNames.length; i++) {
      builder.put(labelNames[i], labelValues[i]);
    }
    final Attributes labels = builder.build();
    BoundLongCounter boundLongCounter = counter.bind(labels);
    return new OpenTelemetryCounter.UnlabelledCounter(boundLongCounter);
  }

  private static class UnlabelledCounter implements Counter {
    private final BoundLongCounter counter;

    private UnlabelledCounter(final BoundLongCounter counter) {
      this.counter = counter;
    }

    @Override
    public void inc() {
      counter.add(1);
    }

    @Override
    public void inc(final long amount) {
      counter.add(amount);
    }
  }
}
