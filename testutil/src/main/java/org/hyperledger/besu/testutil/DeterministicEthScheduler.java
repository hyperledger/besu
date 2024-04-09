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
package org.hyperledger.besu.testutil;

import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

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

  /** Create a new deterministic scheduler that never timeouts */
  public DeterministicEthScheduler() {
    this(TimeoutPolicy.NEVER_TIMEOUT);
  }

  /**
   * Create a new deterministic scheduler with the provided timeout policy
   *
   * @param timeoutPolicy the timeout policy
   */
  public DeterministicEthScheduler(final TimeoutPolicy timeoutPolicy) {
    super(
        new MockExecutorService(),
        new MockScheduledExecutor(),
        new MockExecutorService(),
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
            (MockExecutorService) this.computationExecutor,
            (MockExecutorService) this.blockCreationExecutor);
  }

  /** Test utility for manually running pending futures, when autorun is disabled */
  public void runPendingFutures() {
    executors.forEach(MockExecutorService::runPendingFutures);
  }

  /**
   * Get the count of pending tasks
   *
   * @return the count of pending tasks
   */
  public long getPendingFuturesCount() {
    return executors.stream().mapToLong(MockExecutorService::getPendingFuturesCount).sum();
  }

  /** Expire all pending timeouts */
  public void expirePendingTimeouts() {
    final List<PendingTimeout<?>> toExpire = new ArrayList<>(pendingTimeouts);
    pendingTimeouts.clear();
    toExpire.forEach(PendingTimeout::expire);
  }

  /** Do not automatically run submitted tasks. Tasks can be later run using runPendingFutures */
  public void disableAutoRun() {
    executors.forEach(e -> e.setAutoRun(false));
  }

  /**
   * Get the sync worker mock executor
   *
   * @return the mock executor
   */
  public MockExecutorService mockSyncWorkerExecutor() {
    return (MockExecutorService) syncWorkerExecutor;
  }

  /**
   * Get the scheduled mock executor
   *
   * @return the mock executor
   */
  public MockScheduledExecutor mockScheduledExecutor() {
    return (MockScheduledExecutor) scheduler;
  }

  /**
   * Get the service mock executor
   *
   * @return the mock executor
   */
  public MockExecutorService mockServiceExecutor() {
    return (MockExecutorService) servicesExecutor;
  }

  /**
   * Get the block creation mock executor
   *
   * @return the mock executor
   */
  public MockExecutorService mockBlockCreationExecutor() {
    return (MockExecutorService) blockCreationExecutor;
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

  /** Used to define the timeout behavior of the scheduler */
  @FunctionalInterface
  public interface TimeoutPolicy {
    /** A policy that never timeouts */
    TimeoutPolicy NEVER_TIMEOUT = () -> false;

    /** A policy that timeouts on every task */
    TimeoutPolicy ALWAYS_TIMEOUT = () -> true;

    /**
     * If it should simulate a timeout when called
     *
     * @return true if the scheduler should timeouts
     */
    boolean shouldTimeout();

    /**
     * Create a timeout policy that timeouts x times
     *
     * @param times the number of timeouts
     * @return the timeout policy
     */
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
