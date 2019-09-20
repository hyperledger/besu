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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper around an {@link WritePipe} which allows multiple stages to share the same write pipe.
 * Most operations simply pass through to the underlying pipe but the underlying pipe is only closed
 * when all stages have signalled this pipe should close.
 *
 * @param <T> the type of item in the pipe.
 */
class SharedWritePipe<T> implements WritePipe<T> {
  private final WritePipe<T> delegate;
  private final AtomicInteger remainingClosesRequired;

  /**
   * Creates a new SharedWritePipe.
   *
   * @param delegate the pipe to wrap.
   * @param closesRequired the number of stages this output pipe will be shared with. The underlying
   *     pipe will only be closed when {@link #close()} is called this many times.
   */
  public SharedWritePipe(final WritePipe<T> delegate, final int closesRequired) {
    this.delegate = delegate;
    this.remainingClosesRequired = new AtomicInteger(closesRequired);
  }

  @Override
  public boolean isOpen() {
    return delegate.isOpen();
  }

  @Override
  public void put(final T value) {
    delegate.put(value);
  }

  @Override
  public void close() {
    if (remainingClosesRequired.decrementAndGet() == 0) {
      delegate.close();
    }
  }

  @Override
  public void abort() {
    delegate.abort();
  }

  @Override
  public boolean hasRemainingCapacity() {
    return delegate.hasRemainingCapacity();
  }
}
