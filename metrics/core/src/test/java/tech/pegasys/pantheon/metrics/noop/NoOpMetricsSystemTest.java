/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.metrics.noop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tech.pegasys.pantheon.metrics.StandardMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.metrics.LabelledMetric;
import tech.pegasys.pantheon.plugin.services.metrics.OperationTimer;

import org.junit.Test;

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
