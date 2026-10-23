/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snap2.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** Keeps what is submitted, to run it in turn on the test thread when the test says. */
final class QueuedExecutor extends AbstractExecutorService {
  private final List<Runnable> tasks = new ArrayList<>();

  void runAll() {
    for (final Runnable task : tasks) {
      task.run();
      // a pipeline stage interrupts its thread, the test thread here, once done or aborted
      Thread.interrupted();
    }
  }

  @Override
  public void execute(final Runnable command) {
    tasks.add(command);
  }

  @Override
  public void shutdown() {}

  @Override
  public List<Runnable> shutdownNow() {
    return List.of();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit) {
    return true;
  }
}
