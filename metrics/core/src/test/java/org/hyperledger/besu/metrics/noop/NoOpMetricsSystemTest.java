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
package org.hyperledger.besu.metrics.noop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import org.junit.jupiter.api.Test;

public class NoOpMetricsSystemTest {

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void labelCountsMatchOnCounter() {
    final LabelledMetric<Counter> labeledCounter =
        metricsSystem.createLabelledCounter(
            StandardMetricCategory.PROCESS, "name", "help", "label1");
    assertThat(labeledCounter.labels("one")).isSameAs(NoOpMetricsSystem.NO_OP_COUNTER);
  }

  @Test
  public void failsWheLabelCountsDoNotMatchOnCounter() {
    final LabelledMetric<Counter> labeledCounter =
        metricsSystem.createLabelledCounter(
            StandardMetricCategory.PROCESS, "name", "help", "label1", "label2");

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> labeledCounter.labels("one"))
        .withMessage("The count of labels used must match the count of labels expected.");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> labeledCounter.labels("one", "two", "three"))
        .withMessage("The count of labels used must match the count of labels expected.");
  }

  @Test
  public void labelCountsMatchOnTimer() {
    final LabelledMetric<OperationTimer> labeledTimer =
        metricsSystem.createLabelledTimer(StandardMetricCategory.PROCESS, "name", "help", "label1");
    assertThat(labeledTimer.labels("one")).isSameAs(NoOpMetricsSystem.NO_OP_OPERATION_TIMER);
  }

  @Test
  public void failsWheLabelCountsDoNotMatchOnTimer() {
    final LabelledMetric<OperationTimer> labeledTimer =
        metricsSystem.createLabelledTimer(
            StandardMetricCategory.PROCESS, "name", "help", "label1", "label2");

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> labeledTimer.labels("one"))
        .withMessage("The count of labels used must match the count of labels expected.");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> labeledTimer.labels("one", "two", "three"))
        .withMessage("The count of labels used must match the count of labels expected.");
  }
}
