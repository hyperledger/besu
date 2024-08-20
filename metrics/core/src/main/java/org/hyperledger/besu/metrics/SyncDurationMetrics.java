/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.metrics;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.HashMap;

public class SyncDurationMetrics {

  public static final SyncDurationMetrics NO_METRICS_SYNC_DURATION_METRICS =
      new SyncDurationMetrics(new NoOpMetricsSystem());

  private final LabelledMetric<OperationTimer> timer;

  private final HashMap<String, OperationTimer.TimingContext> timers = new HashMap<>();

  public SyncDurationMetrics(final MetricsSystem metricsSystem) {
    timer =
        metricsSystem.createSimpleLabelledTimer(
            BesuMetricCategory.SYNCHRONIZER, "sync_duration", "Time taken to sync", "name");
  }

  public void startTimer(final Labels label) {
    timers.computeIfAbsent(label.name(), k -> timer.labels(label.name()).startTimer());
  }

  public void stopTimer(final Labels label) {
    OperationTimer.TimingContext context = timers.get(label.name());
    if (context != null) {
      context.stopTimer();
    }
  }

  public enum Labels {
    SYNC_DURATION,
    CHAIN_DOWNLOAD_DURATION,
    SNAP_INITIAL_WORLD_STATE_DOWNLOAD_DURATION,
    SNAP_WORLD_STATE_HEALING_DURATION,
    FAST_WORLD_STATE_DOWNLOAD_DURATION,
    FLAT_DB_HEAL;
  }
}
