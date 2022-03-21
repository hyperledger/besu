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

import static org.hyperledger.besu.util.FutureUtils.propagateResult;

import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.pipeline.Pipeline;
import org.hyperledger.besu.util.ExceptionUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EthScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(EthScheduler.class);

  private final Duration defaultTimeout = Duration.ofSeconds(5);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final CountDownLatch shutdown = new CountDownLatch(1);
  private static final int TX_WORKER_CAPACITY = 1_000;

  protected final ExecutorService syncWorkerExecutor;
  protected final ScheduledExecutorService scheduler;
  protected final ExecutorService txWorkerExecutor;
  protected final ExecutorService servicesExecutor;
  protected final ExecutorService computationExecutor;

  private final Collection<CompletableFuture<?>> pendingFutures = new ConcurrentLinkedDeque<>();

  public EthScheduler(
      final int syncWorkerCount,
      final int txWorkerCount,
      final int computationWorkerCount,
      final MetricsSystem metricsSystem) {
    this(syncWorkerCount, txWorkerCount, TX_WORKER_CAPACITY, computationWorkerCount, metricsSystem);
  }

  public EthScheduler(
      final int syncWorkerCount,
      final int txWorkerCount,
      final int txWorkerQueueSize,
      final int computationWorkerCount,
      final MetricsSystem metricsSystem) {
    this(
        MonitoredExecutors.newFixedThreadPool(
            EthScheduler.class.getSimpleName() + "-Workers", syncWorkerCount, metricsSystem),
        MonitoredExecutors.newScheduledThreadPool(
            EthScheduler.class.getSimpleName() + "-Timer", 1, metricsSystem),
        MonitoredExecutors.newBoundedThreadPool(
            EthScheduler.class.getSimpleName() + "-Transactions",
            txWorkerCount,
            txWorkerQueueSize,
            metricsSystem),
        MonitoredExecutors.newCachedThreadPool(
            EthScheduler.class.getSimpleName() + "-Services", metricsSystem),
        MonitoredExecutors.newFixedThreadPool(
            EthScheduler.class.getSimpleName() + "-Computation",
            computationWorkerCount,
            metricsSystem));
  }

  protected EthScheduler(
      final ExecutorService syncWorkerExecutor,
      final ScheduledExecutorService scheduler,
      final ExecutorService txWorkerExecutor,
      final ExecutorService servicesExecutor,
      final ExecutorService computationExecutor) {
    this.syncWorkerExecutor = syncWorkerExecutor;
    this.scheduler = scheduler;
    this.txWorkerExecutor = txWorkerExecutor;
    this.servicesExecutor = servicesExecutor;
    this.computationExecutor = computationExecutor;
  }

  public <T> CompletableFuture<T> scheduleSyncWorkerTask(
      final Supplier<CompletableFuture<T>> future) {
    final CompletableFuture<T> promise = new CompletableFuture<>();
    final Future<?> workerFuture =
        syncWorkerExecutor.submit(() -> propagateResult(future, promise));
    // If returned promise is cancelled, cancel the worker future
    promise.whenComplete(
        (r, t) -> {
          if (t instanceof CancellationException) {
            workerFuture.cancel(false);
          }
        });
    return promise;
  }

  public void scheduleSyncWorkerTask(final Runnable command) {
    syncWorkerExecutor.execute(command);
  }

  public <T> CompletableFuture<T> scheduleSyncWorkerTask(final EthTask<T> task) {
    final CompletableFuture<T> syncFuture = task.runAsync(syncWorkerExecutor);
    pendingFutures.add(syncFuture);
    syncFuture.whenComplete((r, t) -> pendingFutures.remove(syncFuture));
    return syncFuture;
  }

  public void scheduleTxWorkerTask(final Runnable command) {
    txWorkerExecutor.execute(command);
  }

  public <T> CompletableFuture<T> scheduleServiceTask(final EthTask<T> task) {
    final CompletableFuture<T> serviceFuture = task.runAsync(servicesExecutor);
    pendingFutures.add(serviceFuture);
    serviceFuture.whenComplete((r, t) -> pendingFutures.remove(serviceFuture));
    return serviceFuture;
  }

  public CompletableFuture<Void> startPipeline(final Pipeline<?> pipeline) {
    final CompletableFuture<Void> pipelineFuture = pipeline.start(servicesExecutor);
    pendingFutures.add(pipelineFuture);
    pipelineFuture.whenComplete((r, t) -> pendingFutures.remove(pipelineFuture));
    return pipelineFuture;
  }

  public <T> CompletableFuture<T> scheduleComputationTask(final Supplier<T> computation) {
    return CompletableFuture.supplyAsync(computation, computationExecutor);
  }

  public CompletableFuture<Void> scheduleFutureTask(
      final Runnable command, final Duration duration) {
    final CompletableFuture<Void> promise = new CompletableFuture<>();
    final ScheduledFuture<?> scheduledFuture =
        scheduler.schedule(
            () -> {
              try {
                command.run();
                promise.complete(null);
              } catch (final Throwable t) {
                promise.completeExceptionally(t);
              }
            },
            duration.toMillis(),
            TimeUnit.MILLISECONDS);
    // If returned promise is cancelled, cancel scheduled task
    promise.whenComplete(
        (r, t) -> {
          if (t instanceof CancellationException) {
            scheduledFuture.cancel(false);
          }
        });
    return promise;
  }

  public ScheduledFuture<?> scheduleFutureTaskWithFixedDelay(
      final Runnable command, final Duration initialDelay, final Duration duration) {
    return scheduler.scheduleWithFixedDelay(
        command, initialDelay.toMillis(), duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  public <T> CompletableFuture<T> scheduleFutureTask(
      final Supplier<CompletableFuture<T>> future, final Duration duration) {
    final CompletableFuture<T> promise = new CompletableFuture<>();
    final ScheduledFuture<?> scheduledFuture =
        scheduler.schedule(
            () -> propagateResult(future, promise), duration.toMillis(), TimeUnit.MILLISECONDS);
    // If returned promise is cancelled, cancel scheduled task
    promise.whenComplete(
        (r, t) -> {
          if (t instanceof CancellationException) {
            scheduledFuture.cancel(false);
          }
        });
    return promise;
  }

  public <T> CompletableFuture<T> timeout(final EthTask<T> task) {
    return timeout(task, defaultTimeout);
  }

  public <T> CompletableFuture<T> timeout(final EthTask<T> task, final Duration timeout) {
    final CompletableFuture<T> future = task.run();
    final CompletableFuture<T> result = timeout(future, timeout);
    result.whenComplete(
        (r, error) -> {
          if (errorIsTimeoutOrCancellation(error)) {
            task.cancel();
          }
        });
    return result;
  }

  private boolean errorIsTimeoutOrCancellation(final Throwable error) {
    final Throwable cause = ExceptionUtils.rootCause(error);
    return cause instanceof TimeoutException || cause instanceof CancellationException;
  }

  private <T> CompletableFuture<T> timeout(
      final CompletableFuture<T> future, final Duration delay) {
    final CompletableFuture<T> timeout = failAfterTimeout(delay);
    return future.applyToEither(timeout, Function.identity());
  }

  public void stop() {
    if (stopped.compareAndSet(false, true)) {
      LOG.trace("Stopping " + getClass().getSimpleName());
      syncWorkerExecutor.shutdownNow();
      txWorkerExecutor.shutdownNow();
      scheduler.shutdownNow();
      servicesExecutor.shutdownNow();
      computationExecutor.shutdownNow();
      shutdown.countDown();
    } else {
      LOG.trace("Attempted to stop already stopped " + getClass().getSimpleName());
    }
  }

  public void awaitStop() throws InterruptedException {
    shutdown.await();
    pendingFutures.forEach(future -> future.cancel(true));
    if (!syncWorkerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
      LOG.error("{} worker executor did not shutdown cleanly.", this.getClass().getSimpleName());
    }
    if (!txWorkerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
      LOG.error(
          "{} transaction worker executor did not shutdown cleanly.",
          this.getClass().getSimpleName());
    }
    if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
      LOG.error("{} scheduler did not shutdown cleanly.", this.getClass().getSimpleName());
      scheduler.shutdownNow();
    }
    if (!servicesExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
      LOG.error("{} services executor did not shutdown cleanly.", this.getClass().getSimpleName());
    }
    if (!computationExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
      LOG.error(
          "{} computation executor did not shutdown cleanly.", this.getClass().getSimpleName());
    }
    LOG.trace("{} stopped.", this.getClass().getSimpleName());
  }

  private <T> CompletableFuture<T> failAfterTimeout(final Duration timeout) {
    final CompletableFuture<T> promise = new CompletableFuture<>();
    failAfterTimeout(promise, timeout);
    return promise;
  }

  public <T> void failAfterTimeout(final CompletableFuture<T> promise, final Duration timeout) {
    final long delay = timeout.toMillis();
    final TimeUnit unit = TimeUnit.MILLISECONDS;
    scheduler.schedule(
        () -> {
          final TimeoutException ex =
              new TimeoutException("Timeout after " + delay + " " + unit.name());
          return promise.completeExceptionally(ex);
        },
        delay,
        unit);
  }
}
