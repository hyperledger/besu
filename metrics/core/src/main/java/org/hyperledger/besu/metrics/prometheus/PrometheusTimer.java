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

import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import io.prometheus.client.Summary;

class PrometheusTimer implements LabelledMetric<OperationTimer> {

  private final Summary summary;

  public PrometheusTimer(final Summary summary) {
    this.summary = summary;
  }

  @Override
  public OperationTimer labels(final String... labels) {
    final Summary.Child metric = summary.labels(labels);
    return () -> metric.startTimer()::observeDuration;
  }
}
