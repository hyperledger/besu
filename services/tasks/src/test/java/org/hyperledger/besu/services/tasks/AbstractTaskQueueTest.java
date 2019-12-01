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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

abstract class AbstractTaskQueueTest<T extends TaskCollection<Bytes>> {

  protected abstract T createQueue() throws Exception;

  @Test
  public void enqueueAndDequeue() throws Exception {
    try (final T queue = createQueue()) {
      final Bytes one = Bytes.of(1);
      final Bytes two = Bytes.of(2);
      final Bytes three = Bytes.of(3);

      assertThat(queue.remove()).isNull();

      queue.add(one);
      queue.add(two);
      assertThat(queue.remove().getData()).isEqualTo(one);

      queue.add(three);
      assertThat(queue.remove().getData()).isEqualTo(two);
      assertThat(queue.remove().getData()).isEqualTo(three);
      assertThat(queue.remove()).isNull();
      assertThat(queue.remove()).isNull();

      queue.add(three);
      assertThat(queue.remove().getData()).isEqualTo(three);
    }
  }

  @Test
  public void markTaskFailed() throws Exception {
    try (final T queue = createQueue()) {
      final Bytes value = Bytes.of(1);

      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();

      queue.add(value);

      assertThat(queue.isEmpty()).isFalse();
      assertThat(queue.allTasksCompleted()).isFalse();

      final Task<Bytes> task = queue.remove();
      assertThat(task).isNotNull();
      assertThat(task.getData()).isEqualTo(value);
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isFalse();

      task.markFailed();
      assertThat(queue.isEmpty()).isFalse();
      assertThat(queue.allTasksCompleted()).isFalse();

      // Subsequent mark completed should do nothing
      task.markCompleted();
      assertThat(queue.isEmpty()).isFalse();
      assertThat(queue.allTasksCompleted()).isFalse();
    }
  }

  @Test
  public void markTaskCompleted() throws Exception {
    try (final T queue = createQueue()) {
      final Bytes value = Bytes.of(1);

      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();

      queue.add(value);

      assertThat(queue.isEmpty()).isFalse();
      assertThat(queue.allTasksCompleted()).isFalse();

      final Task<Bytes> task = queue.remove();
      assertThat(task).isNotNull();
      assertThat(task.getData()).isEqualTo(value);
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isFalse();

      task.markCompleted();
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();

      // Subsequent mark failed should do nothing
      task.markFailed();
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();
    }
  }

  @Test
  public void clear() throws Exception {
    try (final T queue = createQueue()) {
      final Bytes one = Bytes.of(1);
      final Bytes two = Bytes.of(2);
      final Bytes three = Bytes.of(3);
      final Bytes four = Bytes.of(4);

      // Fill queue
      queue.add(one);
      queue.add(two);
      assertThat(queue.size()).isEqualTo(2);
      assertThat(queue.isEmpty()).isFalse();
      assertThat(queue.allTasksCompleted()).isFalse();

      // Clear queue and check state
      queue.clear();
      assertThat(queue.size()).isEqualTo(0);
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();
      assertThat(queue.remove()).isNull();

      // Subsequent operations should work as expected
      queue.add(three);
      assertThat(queue.size()).isEqualTo(1);
      queue.add(four);
      assertThat(queue.size()).isEqualTo(2);
      assertThat(queue.remove().getData()).isEqualTo(three);
    }
  }

  @Test
  public void clear_emptyQueueWithOutstandingTasks() throws Exception {
    try (final T queue = createQueue()) {
      final Bytes one = Bytes.of(1);

      // Add and then remove task
      queue.add(one);
      final Task<Bytes> task = queue.remove();
      assertThat(task.getData()).isEqualTo(one);
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isFalse();

      // Clear queue and check state
      queue.clear();
      assertThat(queue.size()).isEqualTo(0);
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();
      assertThat(queue.remove()).isNull();

      // Marking old task as failed should not requeue task
      task.markFailed();
      assertThat(queue.size()).isEqualTo(0);
      assertThat(queue.isEmpty()).isTrue();
      assertThat(queue.allTasksCompleted()).isTrue();
      assertThat(queue.remove()).isNull();
    }
  }

  @Test
  public void handlesConcurrentQueuing() throws Exception {
    final int threadCount = 5;
    final int itemsPerThread = 1000;
    final T queue = createQueue();

    final CountDownLatch dequeueingFinished = new CountDownLatch(1);
    final CountDownLatch queuingFinished = new CountDownLatch(threadCount);

    // Start thread for reading values
    final List<Task<Bytes>> dequeued = new ArrayList<>();
    final Thread reader =
        new Thread(
            () -> {
              while (queuingFinished.getCount() > 0 || !queue.isEmpty()) {
                if (!queue.isEmpty()) {
                  final Task<Bytes> value = queue.remove();
                  value.markCompleted();
                  dequeued.add(value);
                }
              }
              dequeueingFinished.countDown();
            });
    reader.start();

    final Function<Bytes, Thread> queueingThreadFactory =
        (value) ->
            new Thread(
                () -> {
                  try {
                    for (int i = 0; i < itemsPerThread; i++) {
                      queue.add(value);
                    }
                  } finally {
                    queuingFinished.countDown();
                  }
                });

    // Start threads to queue values
    for (int i = 0; i < threadCount; i++) {
      queueingThreadFactory.apply(Bytes.of(i)).start();
    }

    queuingFinished.await();
    dequeueingFinished.await();

    assertThat(dequeued.size()).isEqualTo(threadCount * itemsPerThread);
    assertThat(dequeued.stream().filter(Objects::isNull).count()).isEqualTo(0);
    assertThat(queue.size()).isEqualTo(0);
  }
}
