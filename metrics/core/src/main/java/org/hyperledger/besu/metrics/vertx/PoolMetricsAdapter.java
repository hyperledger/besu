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
package org.hyperledger.besu.metrics.vertx;

import org.hyperledger.besu.plugin.services.metrics.Counter;

import io.vertx.core.spi.metrics.PoolMetrics;

final class PoolMetricsAdapter implements PoolMetrics<Object> {
  private final Counter submittedCounter;
  private final Counter completedCounter;
  private final Counter rejectedCounter;

  public PoolMetricsAdapter(
      final VertxMetricsCollectors vertxMetricsCollectors,
      final String poolType,
      final String poolName) {
    this.submittedCounter =
        vertxMetricsCollectors.submittedLabelledCounter().labels(poolType, poolName);
    this.completedCounter =
        vertxMetricsCollectors.completedLabelledCounter().labels(poolType, poolName);
    this.rejectedCounter =
        vertxMetricsCollectors.rejectedLabelledCounter().labels(poolType, poolName);
  }

  @Override
  public Object submitted() {
    submittedCounter.inc();
    return null;
  }

  @Override
  public void rejected(final Object o) {
    rejectedCounter.inc();
  }

  @Override
  public void end(final Object o, final boolean succeeded) {
    completedCounter.inc();
  }
}
