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

import org.hyperledger.besu.services.tasks.InMemoryTaskQueue;
import org.hyperledger.besu.services.tasks.Task;
import org.hyperledger.besu.services.tasks.TaskCollection;

import java.io.IOException;
import java.nio.file.Path;

public class PersistentTaskCollection<T> implements TaskCollection<T> {

  // The underlying collection
  private final InMemoryTaskQueue<T> wrappedCollection;

  public PersistentTaskCollection(final Path directory) {
    this.wrappedCollection = new InMemoryTaskQueue<>();
  }

  @Override
  public synchronized void add(final T taskData) {
    wrappedCollection.add(taskData);
  }

  @Override
  public synchronized Task<T> remove() {
    return wrappedCollection.remove();
  }

  @Override
  public synchronized void clear() {
    wrappedCollection.clear();
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

  public synchronized void persist() {
    System.out.println("persist list " + wrappedCollection.asList());
  }

  @Override
  public void close() throws IOException {
    wrappedCollection.close();
  }
}
