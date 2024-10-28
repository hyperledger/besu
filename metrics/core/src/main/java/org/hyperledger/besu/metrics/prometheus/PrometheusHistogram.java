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

import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

class PrometheusHistogram implements LabelledMetric<Histogram> {

  private final io.prometheus.client.Histogram histogram;

  public PrometheusHistogram(final io.prometheus.client.Histogram histogram) {
    this.histogram = histogram;
  }

  @Override
  public Histogram labels(final String... labels) {
    return new UnlabelledHistogram(histogram.labels(labels));
  }

  private static class UnlabelledHistogram implements Histogram {
    private final io.prometheus.client.Histogram.Child amount;

    private UnlabelledHistogram(final io.prometheus.client.Histogram.Child amount) {
      this.amount = amount;
    }

    @Override
    public void observe(final double amount) {
      this.amount.observe(amount);
    }
  }
}
