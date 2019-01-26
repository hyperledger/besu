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
 * Represents a very large thread-safe queue that may exceed memory limits.
 *
 * @param <T> the type of data held in the queue
 */
public interface BigQueue<T> extends Closeable {

  void enqueue(T value);

  T dequeue();

  long size();

  default boolean isEmpty() {
    return size() == 0;
  }
}
