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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.testutil.MockExecutorService;

import java.time.Duration;
import java.util.ArrayList;
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
  private final List<PendingTimeout<?>> pendingTimeouts = new ArrayList<>();

  public DeterministicEthScheduler() {
    this(TimeoutPolicy.NEVER_TIMEOUT);
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

  public void expirePendingTimeouts() {
    final List<PendingTimeout<?>> toExpire = new ArrayList<>(pendingTimeouts);
    pendingTimeouts.clear();
    toExpire.forEach(PendingTimeout::expire);
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
    final PendingTimeout<T> pendingTimeout = new PendingTimeout<>(promise, timeout);
    if (timeoutPolicy.shouldTimeout()) {
      pendingTimeout.expire();
    } else {
      this.pendingTimeouts.add(pendingTimeout);
    }
  }

  @FunctionalInterface
  public interface TimeoutPolicy {
    TimeoutPolicy NEVER_TIMEOUT = () -> false;
    TimeoutPolicy ALWAYS_TIMEOUT = () -> true;

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

  private static class PendingTimeout<T> {
    final CompletableFuture<T> promise;
    final Duration timeout;

    private PendingTimeout(final CompletableFuture<T> promise, final Duration timeout) {
      this.promise = promise;
      this.timeout = timeout;
    }

    public void expire() {
      final TimeoutException timeoutException =
          new TimeoutException(
              "Mocked timeout after " + timeout.toMillis() + " " + TimeUnit.MILLISECONDS);
      promise.completeExceptionally(timeoutException);
    }
  }
}
