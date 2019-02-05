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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Before;
import org.junit.Test;

public class EthSchedulerShutdownTest {

  private EthScheduler ethScheduler;
  private ExecutorService syncWorkerExecutor;
  private ScheduledExecutorService scheduledExecutor;
  private ExecutorService txWorkerExecutor;
  private ExecutorService servicesExecutor;
  private ExecutorService computationExecutor;

  @Before
  public void setup() {
    scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    syncWorkerExecutor = Executors.newSingleThreadExecutor();
    txWorkerExecutor = Executors.newSingleThreadExecutor();
    servicesExecutor = Executors.newSingleThreadExecutor();
    computationExecutor = Executors.newSingleThreadExecutor();
    ethScheduler =
        new EthScheduler(
            syncWorkerExecutor,
            scheduledExecutor,
            txWorkerExecutor,
            servicesExecutor,
            computationExecutor);
  }

  @Test
  public void shutdown_syncWorkerShutsDown() throws InterruptedException {
    final MockEthTask task = new MockEthTask(1);

    ethScheduler.scheduleSyncWorkerTask(task::executeTask);
    ethScheduler.stop();

    assertThat(syncWorkerExecutor.isShutdown()).isTrue();
    assertThat(syncWorkerExecutor.isTerminated()).isFalse();

    task.countDown();
    ethScheduler.awaitStop();

    assertThat(syncWorkerExecutor.isShutdown()).isTrue();
    assertThat(syncWorkerExecutor.isTerminated()).isTrue();
  }

  @Test
  public void shutdown_scheduledWorkerShutsDown() throws InterruptedException {
    final MockEthTask task = new MockEthTask(1);

    ethScheduler.scheduleFutureTask(task::executeTask, Duration.ofMillis(1));
    ethScheduler.stop();

    assertThat(scheduledExecutor.isShutdown()).isTrue();
    assertThat(scheduledExecutor.isTerminated()).isFalse();

    task.countDown();
    ethScheduler.awaitStop();

    assertThat(scheduledExecutor.isShutdown()).isTrue();
    assertThat(scheduledExecutor.isTerminated()).isTrue();
  }

  @Test
  public void shutdown_txWorkerShutsDown() throws InterruptedException {
    final MockEthTask task = new MockEthTask(1);

    ethScheduler.scheduleTxWorkerTask(task::executeTask);
    ethScheduler.stop();

    assertThat(txWorkerExecutor.isShutdown()).isTrue();
    assertThat(txWorkerExecutor.isTerminated()).isFalse();

    task.countDown();
    ethScheduler.awaitStop();

    assertThat(txWorkerExecutor.isShutdown()).isTrue();
    assertThat(txWorkerExecutor.isTerminated()).isTrue();
  }

  @Test
  public void shutdown_servicesShutsDown() throws InterruptedException {
    final MockEthTask task = new MockEthTask(1);

    ethScheduler.scheduleServiceTask(task::executeTask);
    ethScheduler.stop();

    assertThat(servicesExecutor.isShutdown()).isTrue();
    assertThat(servicesExecutor.isTerminated()).isFalse();

    task.countDown();
    ethScheduler.awaitStop();

    assertThat(servicesExecutor.isShutdown()).isTrue();
    assertThat(servicesExecutor.isTerminated()).isTrue();
  }

  @Test
  public void shutdown_computationShutsDown() throws InterruptedException {
    final MockEthTask task = new MockEthTask(1);

    ethScheduler.scheduleComputationTask(
        () -> {
          task.executeTask();
          return Integer.MAX_VALUE;
        });
    ethScheduler.stop();

    assertThat(computationExecutor.isShutdown()).isTrue();
    assertThat(computationExecutor.isTerminated()).isFalse();

    task.countDown();
    ethScheduler.awaitStop();

    assertThat(computationExecutor.isShutdown()).isTrue();
    assertThat(computationExecutor.isTerminated()).isTrue();
  }
}
