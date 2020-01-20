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
package org.hyperledger.besu.services.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class CachingTaskCollectionTest {
  private TaskCollection<Bytes> wrappedTaskCollection;

  @Before
  public void setup() {
    wrappedTaskCollection = new InMemoryTaskQueue<>();
  }

  @Test
  public void failTasksFromCache() {
    testFailTasks(10, 5);
  }

  @Test
  public void failTasksOverflowingCache() {
    testFailTasks(10, 20);
  }

  @Test
  public void failTasksWithNoCache() {
    testFailTasks(0, 5);
  }

  private void testFailTasks(final int cacheSize, final int taskCount) {
    final CachingTaskCollection<Bytes> taskCollection = createCachingCollection(cacheSize);

    final List<Bytes> taskData = generateTasks(taskCollection, taskCount);
    assertThat(taskCollection.size()).isEqualTo(taskCount);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    List<Task<Bytes>> tasks = getAllTasks(taskCollection);
    assertThat(taskCollection.size()).isEqualTo(0);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    // Check tasks match what we added
    assertThat(getTaskData(tasks)).containsExactlyInAnyOrder(taskData.toArray(new Bytes[0]));

    // Fail all tasks
    tasks.forEach(Task::markFailed);
    assertThat(taskCollection.size()).isEqualTo(taskCount);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    // Collect tasks again - they should have all been re-added
    tasks = getAllTasks(taskCollection);
    // Check tasks match what we added
    assertThat(getTaskData(tasks)).containsExactlyInAnyOrder(taskData.toArray(new Bytes[0]));

    // Clear tasks and then fail all outstanding tasks
    taskCollection.clear();
    assertThat(taskCollection.isEmpty()).isTrue();
    assertThat(taskCollection.allTasksCompleted()).isTrue();
    // Old failed tasks should not be re-added
    tasks.forEach(Task::markFailed);
    assertThat(taskCollection.isEmpty()).isTrue();
    assertThat(taskCollection.allTasksCompleted()).isTrue();
    assertThat(taskCollection.size()).isEqualTo(0);
  }

  @Test
  public void completeTasksFromCache() {
    testCompleteTasks(10, 9);
  }

  @Test
  public void completeTasksThatOverflowCache() {
    testCompleteTasks(10, 20);
  }

  @Test
  public void completeTasksWithNoCache() {
    testCompleteTasks(0, 20);
  }

  private void testCompleteTasks(final int cacheSize, final int taskCount) {
    final CachingTaskCollection<Bytes> taskCollection = createCachingCollection(cacheSize);

    final List<Bytes> taskData = generateTasks(taskCollection, taskCount);
    assertThat(taskCollection.size()).isEqualTo(taskCount);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    final List<Task<Bytes>> tasks = getAllTasks(taskCollection);
    assertThat(taskCollection.size()).isEqualTo(0);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    // Complete all but last task
    tasks.subList(0, tasks.size() - 1).forEach(Task::markCompleted);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    // Process last task
    tasks.get(tasks.size() - 1).markCompleted();
    assertThat(taskCollection.size()).isEqualTo(0);
    assertThat(taskCollection.allTasksCompleted()).isTrue();

    assertThat(getTaskData(tasks)).containsExactlyInAnyOrder(taskData.toArray(new Bytes[0]));
  }

  @Test
  public void processTasksWithMixedSuccess_cachedTasks() {
    testProcessTasksWithMixedSuccess(10, 5);
  }

  @Test
  public void processTasksWithMixedSuccess_tasksOverflowCache() {
    testProcessTasksWithMixedSuccess(10, 20);
  }

  @Test
  public void processTasksWithMixedSuccess_noCache() {
    testProcessTasksWithMixedSuccess(10, 20);
  }

  private CachingTaskCollection<Bytes> createCachingCollection(final int cacheSize) {
    return new CachingTaskCollection<>(wrappedTaskCollection, cacheSize);
  }

  private void testProcessTasksWithMixedSuccess(final int cacheSize, final int taskCount) {
    final CachingTaskCollection<Bytes> taskCollection = createCachingCollection(cacheSize);

    final List<Bytes> taskData = generateTasks(taskCollection, taskCount);
    assertThat(taskCollection.size()).isEqualTo(taskCount);
    assertThat(taskCollection.allTasksCompleted()).isFalse();

    final List<Task<Bytes>> tasks = getAllTasks(taskCollection);

    final List<Task<Bytes>> failedTasks = new ArrayList<>();
    boolean shouldFail = false;
    for (Task<Bytes> task : tasks) {
      if (shouldFail) {
        task.markFailed();
        failedTasks.add(task);
      } else {
        task.markCompleted();
      }
      shouldFail = !shouldFail;
    }
    assertThat(taskCollection.allTasksCompleted()).isFalse();
    assertThat(taskCollection.size()).isEqualTo(failedTasks.size());

    final List<Bytes> actualTaskData =
        tasks.stream().map(Task::getData).collect(Collectors.toList());
    assertThat(actualTaskData).containsExactlyInAnyOrder(taskData.toArray(new Bytes[0]));

    final List<Task<Bytes>> remainingTasks = getAllTasks(taskCollection);
    assertThat(remainingTasks.size()).isEqualTo(failedTasks.size());
    assertThat(getTaskData(remainingTasks))
        .containsExactlyInAnyOrder(getTaskData(failedTasks).toArray(new Bytes[0]));
  }

  @Test
  public void close() throws IOException {
    final CachingTaskCollection<Bytes> taskCollection = createCachingCollection(10);
    taskCollection.close();
    assertThatThrownBy(() -> taskCollection.add(Bytes.of(1)))
        .isInstanceOf(IllegalStateException.class);
  }

  private List<Bytes> generateTasks(
      final TaskCollection<Bytes> taskCollection, final int taskCount) {
    final List<Bytes> taskData = new ArrayList<>();
    for (int i = 0; i < taskCount; i++) {
      final Bytes value = Bytes.of(i & 0xff);
      taskData.add(value);
      taskCollection.add(value);
    }
    return taskData;
  }

  private List<Bytes> getTaskData(final List<Task<Bytes>> tasks) {
    return tasks.stream().map(Task::getData).collect(Collectors.toList());
  }

  private List<Task<Bytes>> getAllTasks(final TaskCollection<Bytes> taskCollection) {
    final List<Task<Bytes>> tasks = new ArrayList<>();
    while (taskCollection.size() > 0) {
      tasks.add(taskCollection.remove());
    }
    return tasks;
  }
}
