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
package tech.pegasys.pantheon.services.queue;

import java.io.Closeable;

/**
 * Represents a very large thread-safe task queue that may exceed memory limits.
 *
 * @param <T> the type of data held in the queue
 */
public interface TaskQueue<T> extends Closeable {

  /**
   * Enqueue some data for processing.
   *
   * @param taskData The data to be processed.
   */
  void enqueue(T taskData);

  /**
   * Dequeue a task for processing. This task will be tracked as a pending task until either {@code
   * Task.markCompleted} or {@code Task.requeue} is called.
   *
   * @return The task to be processed.
   */
  Task<T> dequeue();

  /** @return The number of tasks in the queue. */
  long size();

  /** @return True if all tasks have been dequeued. */
  boolean isEmpty();

  /** Clear all data from the queue. */
  void clear();

  /** @return True if all tasks have been dequeued and processed. */
  boolean allTasksCompleted();

  interface Task<T> {
    T getData();

    /** Mark this task as completed. */
    void markCompleted();

    /** Mark this task as failed and requeue. */
    void markFailed();
  }
}
