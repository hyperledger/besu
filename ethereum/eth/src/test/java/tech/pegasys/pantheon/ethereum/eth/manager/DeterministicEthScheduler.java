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

import tech.pegasys.pantheon.testutil.MockExecutorService;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Schedules tasks that run immediately and synchronously for testing. */
public class DeterministicEthScheduler extends EthScheduler {

  private final TimeoutPolicy timeoutPolicy;
  private final List<MockExecutorService> executors;

  public DeterministicEthScheduler() {
    this(TimeoutPolicy.NEVER);
  }

  public DeterministicEthScheduler(final TimeoutPolicy timeoutPolicy) {
    super(
        new MockExecutorService(),
        new MockScheduledExecutor(),
        new MockExecutorService(),
        new MockExecutorService(),
        new MockExecutorService());

    this.timeoutPolicy = timeoutPolicy;
    this.executors =
        Arrays.asList(
            (MockExecutorService) this.syncWorkerExecutor,
            (MockExecutorService) this.scheduler,
            (MockExecutorService) this.txWorkerExecutor,
            (MockExecutorService) this.servicesExecutor,
            (MockExecutorService) this.computationExecutor);
  }

  // Test utility for running pending futures
  public void runPendingFutures() {
    executors.forEach(MockExecutorService::runPendingFutures);
  }

  public long getPendingFuturesCount() {
    return executors.stream().mapToLong(MockExecutorService::getPendingFuturesCount).sum();
  }

  public void disableAutoRun() {
    executors.forEach(e -> e.setAutoRun(false));
  }

  MockExecutorService mockSyncWorkerExecutor() {
    return (MockExecutorService) syncWorkerExecutor;
  }

  MockScheduledExecutor mockScheduledExecutor() {
    return (MockScheduledExecutor) scheduler;
  }

  public MockExecutorService mockServiceExecutor() {
    return (MockExecutorService) servicesExecutor;
  }

  @Override
  public <T> void failAfterTimeout(final CompletableFuture<T> promise, final Duration timeout) {
    if (timeoutPolicy.shouldTimeout()) {
      final TimeoutException timeoutException =
          new TimeoutException(
              "Mocked timeout after " + timeout.toMillis() + " " + TimeUnit.MILLISECONDS);
      promise.completeExceptionally(timeoutException);
    }
  }

  @FunctionalInterface
  public interface TimeoutPolicy {
    TimeoutPolicy NEVER = () -> false;
    TimeoutPolicy ALWAYS = () -> true;

    boolean shouldTimeout();

    static TimeoutPolicy timeoutXTimes(final int times) {
      final AtomicInteger timeouts = new AtomicInteger(times);
      return () -> {
        if (timeouts.get() <= 0) {
          return false;
        }
        timeouts.decrementAndGet();
        return true;
      };
    }
  }
}
