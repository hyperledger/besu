/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Lists;
import org.junit.Test;

public class AbstractEthTaskTest {

  @Test
  public void shouldCancelAllIncompleteSubtasksWhenMultipleIncomplete() {
    final CompletableFuture<Void> subtask1 = new CompletableFuture<>();
    final CompletableFuture<Void> subtask2 = new CompletableFuture<>();
    final EthTaskWithMultipleSubtasks task =
        new EthTaskWithMultipleSubtasks(Lists.newArrayList(subtask1, subtask2));

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
        new EthTaskWithMultipleSubtasks(Lists.newArrayList(subtask1, subtask2, subtask3));

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
        new EthTaskWithMultipleSubtasks(Lists.newArrayList(subtask1, subtask2));

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

  private class EthTaskWithMultipleSubtasks extends AbstractEthTask<Void> {

    private final List<CompletableFuture<?>> subtasks;

    private EthTaskWithMultipleSubtasks(final List<CompletableFuture<?>> subtasks) {
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
            result.get().complete(null);
          });
    }
  }
}
