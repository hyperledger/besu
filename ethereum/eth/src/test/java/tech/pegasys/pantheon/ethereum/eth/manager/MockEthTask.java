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
package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.util.concurrent.CountDownLatch;

public class MockEthTask extends AbstractEthTask<Object> {

  private boolean executed = false;
  private CountDownLatch countdown;

  MockEthTask(final int count) {
    super(NoOpMetricsSystem.NO_OP_LABELLED_TIMER);
    countdown = new CountDownLatch(count);
  }

  MockEthTask() {
    this(0);
  }

  @Override
  protected void executeTask() {
    try {
      countdown.await();
    } catch (final InterruptedException ignore) {
    }
    executed = true;
  }

  void countDown() {
    countdown.countDown();
  }

  boolean hasBeenStarted() {
    return executed;
  }

  void complete() {
    if (executed) {
      result.get().complete(null);
    }
  }

  void fail() {
    result.get().completeExceptionally(new RuntimeException("Failure forced for testing"));
  }
}
