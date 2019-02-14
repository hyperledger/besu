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
package tech.pegasys.pantheon.services.queue;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryTaskQueue<T> implements TaskQueue<T> {
  private final Queue<T> internalQueue = new ArrayDeque<>();
  private final AtomicInteger unfinishedOutstandingTasks = new AtomicInteger(0);
  private final AtomicBoolean closed = new AtomicBoolean(false);

  @Override
  public synchronized void enqueue(final T taskData) {
    assertNotClosed();
    internalQueue.add(taskData);
  }

  @Override
  public synchronized Task<T> dequeue() {
    assertNotClosed();
    T data = internalQueue.poll();
    if (data == null) {
      return null;
    }
    unfinishedOutstandingTasks.incrementAndGet();
    return new InMemoryTask<>(this, data);
  }

  @Override
  public synchronized long size() {
    assertNotClosed();
    return internalQueue.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    assertNotClosed();
    return size() == 0;
  }

  @Override
  public boolean allTasksCompleted() {
    assertNotClosed();
    return isEmpty() && unfinishedOutstandingTasks.get() == 0;
  }

  @Override
  public synchronized void close() {
    closed.set(true);
    internalQueue.clear();
  }

  private void assertNotClosed() {
    if (closed.get()) {
      throw new IllegalStateException("Attempt to access closed " + getClass().getSimpleName());
    }
  }

  private synchronized void handleFailedTask(final InMemoryTask<T> task) {
    enqueue(task.getData());
    markTaskCompleted();
  }

  private synchronized void markTaskCompleted() {
    unfinishedOutstandingTasks.decrementAndGet();
  }

  private static class InMemoryTask<T> implements Task<T> {
    private final T data;
    private final InMemoryTaskQueue<T> queue;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public InMemoryTask(final InMemoryTaskQueue<T> queue, final T data) {
      this.queue = queue;
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }

    @Override
    public void markCompleted() {
      if (completed.compareAndSet(false, true)) {
        queue.markTaskCompleted();
      }
    }

    @Override
    public void markFailed() {
      if (completed.compareAndSet(false, true)) {
        queue.handleFailedTask(this);
      }
    }
  }
}
