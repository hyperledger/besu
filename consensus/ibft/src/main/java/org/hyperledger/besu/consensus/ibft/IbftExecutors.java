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

package org.hyperledger.besu.consensus.ibft;

import static org.hyperledger.besu.ethereum.eth.manager.MonitoredExecutors.newScheduledThreadPool;

import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftExecutors {

  private enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  private static final Logger LOG = LogManager.getLogger();

  private final Duration shutdownTimeout = Duration.ofSeconds(30);
  private final MetricsSystem metricsSystem;

  private volatile ScheduledExecutorService timerExecutor;
  private volatile ExecutorService ibftProcessorExecutor;
  private volatile State state = State.IDLE;

  private IbftExecutors(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
  }

  public static IbftExecutors create(final MetricsSystem metricsSystem) {
    return new IbftExecutors(metricsSystem);
  }

  public synchronized void start() {
    if (state != State.IDLE) {
      // Nothing to do
      return;
    }
    state = State.RUNNING;
    ibftProcessorExecutor = Executors.newSingleThreadExecutor();
    timerExecutor = newScheduledThreadPool("IbftTimerExecutor", 1, metricsSystem);
  }

  public void stop() {
    synchronized (this) {
      if (state != State.RUNNING) {
        return;
      }
      state = State.STOPPED;
    }

    timerExecutor.shutdownNow();
    ibftProcessorExecutor.shutdownNow();
  }

  public void awaitStop() throws InterruptedException {
    if (!timerExecutor.awaitTermination(shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
      LOG.error("{} timer executor did not shutdown cleanly.", getClass().getSimpleName());
    }
    if (!ibftProcessorExecutor.awaitTermination(shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
      LOG.error("{} ibftProcessor executor did not shutdown cleanly.", getClass().getSimpleName());
    }
  }

  public synchronized void executeIbftProcessor(final IbftProcessor ibftProcessor) {
    assertRunning();
    ibftProcessorExecutor.execute(ibftProcessor);
  }

  public synchronized ScheduledFuture<?> scheduleTask(
      final Runnable command, final long delay, final TimeUnit unit) {
    assertRunning();
    return timerExecutor.schedule(command, delay, unit);
  }

  private void assertRunning() {
    if (state != State.RUNNING) {
      throw new IllegalStateException(
          "Attempt to interact with "
              + getClass().getSimpleName()
              + " that is not running. Current State is "
              + state.name()
              + ".");
    }
  }
}
