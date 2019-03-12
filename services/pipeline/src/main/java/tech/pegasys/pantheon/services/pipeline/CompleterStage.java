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
package tech.pegasys.pantheon.services.pipeline;

import tech.pegasys.pantheon.metrics.Counter;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

class CompleterStage<T> implements Runnable {
  private final ReadPipe<T> input;
  private final Consumer<T> completer;
  private final Counter outputCounter;
  private final CompletableFuture<?> future = new CompletableFuture<>();

  CompleterStage(
      final ReadPipe<T> input, final Consumer<T> completer, final Counter outputCounter) {
    this.input = input;
    this.completer = completer;
    this.outputCounter = outputCounter;
  }

  @Override
  public void run() {
    while (input.hasMore()) {
      final T value = input.get();
      if (value != null) {
        completer.accept(value);
        outputCounter.inc();
      }
    }
    future.complete(null);
  }

  public CompletableFuture<?> getFuture() {
    return future;
  }
}
