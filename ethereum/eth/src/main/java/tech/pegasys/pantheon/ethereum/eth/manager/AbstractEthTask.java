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

import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public abstract class AbstractEthTask<T> implements EthTask<T> {

  protected double taskTimeInSec = -1.0D;
  protected final LabelledMetric<OperationTimer> ethTasksTimer;
  protected final OperationTimer taskTimer;
  protected final AtomicReference<CompletableFuture<T>> result = new AtomicReference<>();
  protected volatile Collection<CompletableFuture<?>> subTaskFutures =
      new ConcurrentLinkedDeque<>();

  /** @param ethTasksTimer The metrics timer to use to time the duration of the task. */
  protected AbstractEthTask(final LabelledMetric<OperationTimer> ethTasksTimer) {
    this.ethTasksTimer = ethTasksTimer;
    taskTimer = ethTasksTimer.labels(getClass().getSimpleName());
  }

  @Override
  public final CompletableFuture<T> run() {
    if (result.compareAndSet(null, new CompletableFuture<>())) {
      executeTaskTimed();
      result
          .get()
          .whenComplete(
              (r, t) -> {
                cleanup();
              });
    }
    return result.get();
  }

  @Override
  public final void cancel() {
    synchronized (result) {
      result.compareAndSet(null, new CompletableFuture<>());
      result.get().cancel(false);
    }
  }

  public final boolean isDone() {
    return result.get() != null && result.get().isDone();
  }

  public final boolean isSucceeded() {
    return isDone() && !result.get().isCompletedExceptionally();
  }

  public final boolean isFailed() {
    return isDone() && result.get().isCompletedExceptionally();
  }

  public final boolean isCancelled() {
    return isDone() && result.get().isCancelled();
  }

  /**
   * Utility for executing completable futures that handles cleanup if this EthTask is cancelled.
   *
   * @param subTask a subTask to execute
   * @param <S> the type of data returned from the CompletableFuture
   * @return The completableFuture that was executed
   */
  protected final <S> CompletableFuture<S> executeSubTask(
      final Supplier<CompletableFuture<S>> subTask) {
    synchronized (result) {
      if (!isCancelled()) {
        final CompletableFuture<S> subTaskFuture = subTask.get();
        subTaskFutures.add(subTaskFuture);
        subTaskFuture.whenComplete(
            (r, t) -> {
              subTaskFutures.remove(subTaskFuture);
            });
        return subTaskFuture;
      } else {
        final CompletableFuture<S> future = new CompletableFuture<>();
        future.completeExceptionally(new CancellationException());
        return future;
      }
    }
  }

  /**
   * Helper method for sending subTask to worker that will clean up if this EthTask is cancelled.
   *
   * @param scheduler the scheduler that will run worker task
   * @param subTask a subTask to execute
   * @param <S> the type of data returned from the CompletableFuture
   * @return The completableFuture that was executed
   */
  protected final <S> CompletableFuture<S> executeWorkerSubTask(
      final EthScheduler scheduler, final Supplier<CompletableFuture<S>> subTask) {
    return executeSubTask(() -> scheduler.scheduleSyncWorkerTask(subTask));
  }

  public final T result() {
    if (!isSucceeded()) {
      return null;
    }
    try {
      return result.get().get();
    } catch (final InterruptedException | ExecutionException e) {
      return null;
    }
  }

  /** Execute core task logic. */
  protected abstract void executeTask();

  /** Executes the task while timed by a timer. */
  public void executeTaskTimed() {
    final OperationTimer.TimingContext timingContext = taskTimer.startTimer();
    try {
      executeTask();
    } finally {
      taskTimeInSec = timingContext.stopTimer();
    }
  }

  public double getTaskTimeInSec() {
    return taskTimeInSec;
  }

  /** Cleanup any resources when task completes. */
  protected void cleanup() {
    for (final CompletableFuture<?> subTaskFuture : subTaskFutures) {
      subTaskFuture.cancel(false);
    }
  }
}
