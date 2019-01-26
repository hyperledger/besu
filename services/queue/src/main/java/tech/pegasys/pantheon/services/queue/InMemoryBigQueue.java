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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InMemoryBigQueue<T> implements BigQueue<T> {
  private final Queue<T> internalQueue = new ConcurrentLinkedQueue<>();

  @Override
  public void enqueue(final T value) {
    internalQueue.add(value);
  }

  @Override
  public T dequeue() {
    return internalQueue.poll();
  }

  @Override
  public long size() {
    return internalQueue.size();
  }

  @Override
  public void close() {
    internalQueue.clear();
  }
}
