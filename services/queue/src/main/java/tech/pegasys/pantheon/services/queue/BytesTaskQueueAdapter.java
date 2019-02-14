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

public class BytesTaskQueueAdapter<T> implements TaskQueue<T> {

  private final BytesTaskQueue queue;
  private final Function<T, BytesValue> serializer;
  private final Function<BytesValue, T> deserializer;

  public BytesTaskQueueAdapter(
      final BytesTaskQueue queue,
      final Function<T, BytesValue> serializer,
      final Function<BytesValue, T> deserializer) {
    this.queue = queue;
    this.serializer = serializer;
    this.deserializer = deserializer;
  }

  @Override
  public void enqueue(final T taskData) {
    queue.enqueue(serializer.apply(taskData));
  }

  @Override
  public Task<T> dequeue() {
    Task<BytesValue> task = queue.dequeue();
    if (task == null) {
      return null;
    }

    T data = deserializer.apply(task.getData());
    return new AdapterTask<>(task, data);
  }

  @Override
  public long size() {
    return queue.size();
  }

  @Override
  public boolean isEmpty() {
    return queue.isEmpty();
  }

  @Override
  public boolean allTasksCompleted() {
    return queue.allTasksCompleted();
  }

  @Override
  public void close() throws IOException {
    queue.close();
  }

  private static class AdapterTask<T> implements Task<T> {
    private final Task<BytesValue> wrappedTask;
    private final T data;

    public AdapterTask(final Task<BytesValue> wrappedTask, final T data) {
      this.wrappedTask = wrappedTask;
      this.data = data;
    }

    @Override
    public T getData() {
      return data;
    }

    @Override
    public void markCompleted() {
      wrappedTask.markCompleted();
    }

    @Override
    public void markFailed() {
      wrappedTask.markFailed();
    }
  }
}
