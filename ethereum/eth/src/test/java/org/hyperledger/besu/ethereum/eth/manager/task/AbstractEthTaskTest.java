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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.testutil.MockExecutorService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AbstractEthTaskTest {

  @Mock private OperationTimer mockOperationTimer;
  @Mock private OperationTimer.TimingContext mockTimingContext;

  @Test
  public void shouldCancelAllIncompleteSubtasksWhenMultipleIncomplete() {
    final CompletableFuture<Void> subtask1 = new CompletableFuture<>();
    final CompletableFuture<Void> subtask2 = new CompletableFuture<>();
    final EthTaskWithMultipleSubtasks task =
        new EthTaskWithMultipleSubtasks(Arrays.asList(subtask1, subtask2));

    task.run();
    task.cancel();

    assertThat(subtask1.isCancelled()).isTrue();
    assertThat(subtask2.isCancelled()).isTrue();
  }

  @Test
  public void shouldAnyCancelIncompleteSubtasksWhenMultiple() {
    final CompletableFuture<Void> subtask1 = new CompletableFuture<>();
    final CompletableFuture<Void> subtask2 = new CompletableFuture<>();
    final CompletableFuture<Void> subtask3 = new CompletableFuture<>();
    final EthTaskWithMultipleSubtasks task =
        new EthTaskWithMultipleSubtasks(Arrays.asList(subtask1, subtask2, subtask3));

    task.run();
    subtask1.complete(null);
    task.cancel();

    assertThat(subtask1.isCancelled()).isFalse();
    assertThat(subtask2.isCancelled()).isTrue();
    assertThat(subtask3.isCancelled()).isTrue();
    assertThat(task.isCancelled()).isTrue();
  }

  @Test
  public void shouldCompleteWhenCancelNotCalled() {
    final AtomicBoolean done = new AtomicBoolean(false);
    final CompletableFuture<Void> subtask1 = new CompletableFuture<>();
    final CompletableFuture<Void> subtask2 = new CompletableFuture<>();
    final EthTaskWithMultipleSubtasks task =
        new EthTaskWithMultipleSubtasks(Arrays.asList(subtask1, subtask2));

    final CompletableFuture<Void> future = task.run();
    subtask1.complete(null);
    subtask2.complete(null);
    task.cancel();

    future.whenComplete(
        (result, error) -> {
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
  }

  @Test
  public void shouldIgnoreMultipleRuns() {
    final AtomicInteger calls = new AtomicInteger(0);
    final AbstractEthTask<Void> incrementingTask =
        new AbstractEthTask<>(new NoOpMetricsSystem()) {

          @Override
          protected void executeTask() {
            calls.incrementAndGet();
            result.complete(null);
          }
        };

    incrementingTask.run();
    incrementingTask.run();
    incrementingTask.runAsync(new MockExecutorService());
    assertThat(calls).hasValue(1);
  }

  @Test
  public void shouldInvokeTimingMethods() {
    final AbstractEthTask<Void> task =
        new AbstractEthTask<Void>(mockOperationTimer) {
          @Override
          protected void executeTask() {}
        };
    when(mockOperationTimer.startTimer()).thenReturn(mockTimingContext);
    task.run();
    verify(mockOperationTimer).startTimer();
    verify(mockTimingContext).stopTimer();
  }

  @Test
  public void shouldHaveSpecificMetricsLabels() {
    // seed with a failing value so that a no-op also trips the failure.
    final String[] lastLabelNames = {"AbstractEthTask"};
    final MetricsSystem instrumentedLabeler =
        new NoOpMetricsSystem() {
          @Override
          public LabelledMetric<OperationTimer> createLabelledTimer(
              final MetricCategory category,
              final String name,
              final String help,
              final String... labelNames) {
            return names -> {
              lastLabelNames[0] = names[0];
              return null;
            };
          }
        };
    new AbstractEthTask<>(instrumentedLabeler) {
      @Override
      protected void executeTask() {
        // no-op
      }
    };
    assertThat(lastLabelNames[0]).isNotEqualTo("AbstractEthTask");
  }

  private class EthTaskWithMultipleSubtasks extends AbstractEthTask<Void> {

    private final List<CompletableFuture<?>> subtasks;

    private EthTaskWithMultipleSubtasks(final List<CompletableFuture<?>> subtasks) {
      super(new NoOpMetricsSystem());
      this.subtasks = subtasks;
    }

    @Override
    protected void executeTask() {
      final List<CompletableFuture<?>> completedSubTasks = Lists.newArrayList();
      for (final CompletableFuture<?> subtask : subtasks) {
        final CompletableFuture<?> completedSubTask = executeSubTask(() -> subtask);
        completedSubTasks.add(completedSubTask);
      }

      final CompletableFuture<?> executedAllSubtasks =
          CompletableFuture.allOf(
              completedSubTasks.toArray(new CompletableFuture<?>[completedSubTasks.size()]));
      executedAllSubtasks.whenComplete(
          (r, t) -> {
            result.complete(null);
          });
    }
  }
}
