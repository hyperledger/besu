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
package tech.pegasys.pantheon.metrics;

import java.util.concurrent.atomic.AtomicInteger;

/** Counter that triggers a specific task each time a step is hit. */
public class RunnableCounter implements Counter {

  private final Counter backedCounter;
  private final Runnable task;
  private final int step;
  private AtomicInteger stepCounter;

  public RunnableCounter(final Counter backedCounter, final Runnable task, final int step) {
    this.backedCounter = backedCounter;
    this.task = task;
    this.step = step;
    this.stepCounter = new AtomicInteger(0);
  }

  /**
   * Increments the stepCounter by 1
   *
   * <p>{@link #inc(long) inc} method
   */
  @Override
  public void inc() {
    this.inc(1);
  }

  /**
   * Increments the stepCounter by amount. Triggers the runnable if the step is hit.
   *
   * @param amount the value to add to the stepCounter.
   */
  @Override
  public void inc(final long amount) {
    stepCounter.addAndGet((int) amount);
    backedCounter.inc(amount);
    if (stepCounter.get() == step) {
      task.run();
      stepCounter = new AtomicInteger(0);
    }
  }
}
