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
package org.hyperledger.besu.services.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

class CompleterStage<T> implements Stage {
  private final ReadPipe<T> input;
  private final Consumer<T> completer;
  private final String name;
  private final CompletableFuture<?> future = new CompletableFuture<>();

  CompleterStage(final String name, final ReadPipe<T> input, final Consumer<T> completer) {
    this.input = input;
    this.completer = completer;
    this.name = name;
  }

  @Override
  public void run() {
    while (input.hasMore()) {
      final T value = input.get();
      if (value != null) {
        completer.accept(value);
      }
    }
    future.complete(null);
  }

  public CompletableFuture<?> getFuture() {
    return future;
  }

  @Override
  public String getName() {
    return name;
  }
}
