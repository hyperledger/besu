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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftExecutors {

  private static final Logger LOG = LogManager.getLogger();

  private final ScheduledExecutorService timerExecutor;
  private final Queue<ExecutorService> processorExecutor = new ConcurrentLinkedQueue<>();
  private boolean started = false;
  private boolean stopped = false;

  private IbftExecutors(final MetricsSystem metricsSystem) {
    timerExecutor = newScheduledThreadPool("IbftTimerExecutor", 1, metricsSystem);
  }

  public static IbftExecutors create(final MetricsSystem metricsSystem) {
    return new IbftExecutors(metricsSystem);
  }

  public synchronized void start() {
    started = true;
  }

  public synchronized void stop() {
    synchronized (this) {
      if (started && !stopped) {
        stopped = true;
      } else {
        return;
      }
    }

    timerExecutor.shutdownNow();
    for (final ExecutorService executorService : processorExecutor) {
      executorService.shutdownNow();
    }
  }

  public void awaitStop() {
    try {
      timerExecutor.awaitTermination(5, TimeUnit.SECONDS);
      for (ExecutorService executorService : processorExecutor) {
        executorService.awaitTermination(5, TimeUnit.SECONDS);
      }
    } catch (final InterruptedException e) {
      LOG.error("{} failed to shutdown cleanly.", getClass().getSimpleName(), e);
    }
  }

  public synchronized void executeOnSingleThread(final Runnable runnable) {
    assertRunning();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    processorExecutor.add(executor);
    executor.execute(runnable);
  }

  public synchronized ScheduledFuture<?> scheduleTask(
      final Runnable command, final long delay, final TimeUnit unit) {
    assertRunning();
    return timerExecutor.schedule(command, delay, unit);
  }

  private void assertRunning() {
    if (!started) {
      throw new IllegalStateException(
          "Attempt to interact with " + getClass().getSimpleName() + " before invoking start().");
    }
    if (stopped) {
      throw new IllegalStateException(
          "Attempt to interacted with a stopped " + getClass().getSimpleName() + ".");
    }
  }
}
