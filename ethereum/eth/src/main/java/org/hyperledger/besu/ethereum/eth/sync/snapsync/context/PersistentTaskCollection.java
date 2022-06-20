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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.context;

import org.hyperledger.besu.services.tasks.FlatFileTaskCollection;
import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class PersistentTaskCollection<T> implements TaskCollection<T> {

  // The underlying collection
  private final TaskCollection<T> wrappedCollection;

  private final FlatFileTaskCollection<T> persistedTasks;

  private boolean closed = false;

  public PersistentTaskCollection(final FlatFileTaskCollection<T> persistedTasks) {
    this.wrappedCollection = new InMemoryTaskQueue<>();
    this.persistedTasks = persistedTasks;
  }

  @Override
  public synchronized void add(final T taskData) {
    assertNotClosed();
    wrappedCollection.add(taskData);
    persistedTasks.add(taskData);
  }

  @Override
  public synchronized Task<T> remove() {
    assertNotClosed();
    return new PersistedTask<T>(this, wrappedCollection.remove().getData());
  }

  @Override
  public synchronized void clear() {
    assertNotClosed();
    wrappedCollection.clear();
    persistedTasks.clear();
  }

  @Override
  public synchronized long size() {
    return wrappedCollection.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return wrappedCollection.isEmpty();
  }

  /** @return True if all tasks have been removed and processed. */
  @Override
  public synchronized boolean allTasksCompleted() {
    return wrappedCollection.allTasksCompleted();
  }

  private synchronized boolean markTaskCompleted(final PersistedTask<T> task) {
    // this is not a problem because this list is always small
    Task<T> remove = persistedTasks.remove();
    while (!remove.getData().equals(task)) {
      persistedTasks.add(task.getData());
    }
    return true;
  }

  @Override
  public synchronized void close() throws IOException {
    wrappedCollection.close();
    persistedTasks.close();
    closed = true;
  }

  private void assertNotClosed() {
    if (closed) {
      throw new IllegalStateException("Attempt to access closed " + getClass().getSimpleName());
    }
  }

  private static class PersistedTask<T> implements Task<T> {
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final PersistentTaskCollection<T> parentQueue;
    private final T data;

    private PersistedTask(final PersistentTaskCollection<T> parentQueue, final T data) {
      this.parentQueue = parentQueue;
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }

    @Override
    public void markCompleted() {
      if (completed.compareAndSet(false, true)) {
        parentQueue.markTaskCompleted(this);
      }
    }

    @Override
    public void markFailed() {}
  }
}
