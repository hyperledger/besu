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

package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.ethereum.eth.manager.MonitoredExecutors;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BftExecutors {

  private enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  public enum ConsensusType {
    IBFT,
    QBFT
  }

  private static final Logger LOG = LoggerFactory.getLogger(BftExecutors.class);

  private final Duration shutdownTimeout = Duration.ofSeconds(30);
  private final MetricsSystem metricsSystem;
  private final ConsensusType consensusType;

  private volatile ScheduledExecutorService timerExecutor;
  private volatile ExecutorService bftProcessorExecutor;
  private volatile State state = State.IDLE;

  private BftExecutors(final MetricsSystem metricsSystem, final ConsensusType consensusType) {
    this.metricsSystem = metricsSystem;
    this.consensusType = consensusType;
  }

  public static BftExecutors create(
      final MetricsSystem metricsSystem, final ConsensusType consensusType) {
    return new BftExecutors(metricsSystem, consensusType);
  }

  public synchronized void start() {
    if (state != State.IDLE) {
      // Nothing to do
      return;
    }
    state = State.RUNNING;
    final ThreadFactory namedThreadFactory =
        new ThreadFactoryBuilder()
            .setNameFormat("BftProcessorExecutor-" + consensusType.name() + "-%d")
            .build();
    bftProcessorExecutor = Executors.newSingleThreadExecutor(namedThreadFactory);
    timerExecutor =
        MonitoredExecutors.newScheduledThreadPool(
            "BftTimerExecutor-" + consensusType.name(), 1, metricsSystem);
  }

  public void stop() {
    synchronized (this) {
      if (state != State.RUNNING) {
        return;
      }
      state = State.STOPPED;
    }
    timerExecutor.shutdownNow();
    bftProcessorExecutor.shutdownNow();
  }

  public void awaitStop() throws InterruptedException {
    if (!timerExecutor.awaitTermination(shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
      LOG.error("{} timer executor did not shutdown cleanly.", getClass().getSimpleName());
    }
    if (!bftProcessorExecutor.awaitTermination(shutdownTimeout.getSeconds(), TimeUnit.SECONDS)) {
      LOG.error("{} bftProcessor executor did not shutdown cleanly.", getClass().getSimpleName());
    }
  }

  public synchronized void executeBftProcessor(final BftProcessor bftProcessor) {
    assertRunning();
    bftProcessorExecutor.execute(bftProcessor);
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
