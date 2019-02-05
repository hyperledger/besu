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

import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.function.Function;

public class BytesQueueAdapter<T> implements BigQueue<T> {

  private final BytesQueue queue;
  private final Function<T, BytesValue> serializer;
  private final Function<BytesValue, T> deserializer;

  public BytesQueueAdapter(
      final BytesQueue queue,
      final Function<T, BytesValue> serializer,
      final Function<BytesValue, T> deserializer) {
    this.queue = queue;
    this.serializer = serializer;
    this.deserializer = deserializer;
  }

  @Override
  public void enqueue(final T value) {
    queue.enqueue(serializer.apply(value));
  }

  @Override
  public T dequeue() {
    BytesValue value = queue.dequeue();
    return value == null ? null : deserializer.apply(value);
  }

  @Override
  public long size() {
    return queue.size();
  }

  @Override
  public void close() throws IOException {
    queue.close();
  }
}
