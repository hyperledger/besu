/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.handlers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A CompletableFuture that automatically interrupts its worker thread when cancelled with
 * mayInterruptIfRunning=true. This is useful for cancelling long-running operations when a client
 * connection is closed.
 *
 * @param <T> the result type
 */
public class InterruptibleCompletableFuture<T> extends CompletableFuture<T> {
  private final AtomicReference<Thread> workerThreadRef = new AtomicReference<>();

  /**
   * Set the worker thread that should be interrupted if this future is cancelled.
   *
   * @param thread the worker thread
   */
  public void setWorkerThread(final Thread thread) {
    workerThreadRef.set(thread);
  }

  /** Clear the worker thread reference, typically called when the worker completes. */
  public void clearWorkerThread() {
    workerThreadRef.set(null);
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    boolean cancelled = super.cancel(mayInterruptIfRunning);

    // If successfully cancelled and interruption is requested, interrupt the worker
    if (cancelled && mayInterruptIfRunning) {
      Thread workerThread = workerThreadRef.get();
      if (workerThread != null && workerThread.isAlive()) {
        workerThread.interrupt();
      }
    }

    return cancelled;
  }
}
