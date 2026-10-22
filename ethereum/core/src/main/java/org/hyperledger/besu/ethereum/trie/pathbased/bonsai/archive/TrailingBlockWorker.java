/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive;

import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.io.Closeable;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A background worker that trails the chain head: it follows a target block that advances with new
 * canonical heads and runs single-flight catch-ups on its own executor until its position reaches
 * the target.
 *
 * <p>Subclasses supply the target ({@link #targetFor}), their progress ({@link #position}) and the
 * work ({@link #catchUp}). A catch-up that throws is retried with exponential backoff, and the
 * worker can be {@linkplain #halt halted} when retrying cannot help.
 */
public abstract class TrailingBlockWorker implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(TrailingBlockWorker.class);

  private static final long DEFAULT_INITIAL_RETRY_DELAY_MILLIS = 1_000L;
  private static final long DEFAULT_MAX_RETRY_DELAY_MILLIS = 60_000L;

  private final Blockchain blockchain;
  private final ScheduledExecutorService executorService;
  private final long closeTimeoutSeconds;

  /** The highest block the worker should reach; only ever raised. */
  protected final AtomicLong targetBlock = new AtomicLong(0L);

  /** Set while a catch-up is queued, running or backing off, so only one is ever in flight. */
  protected final AtomicBoolean catchUpRunning = new AtomicBoolean(false);

  /** The registered block observer, empty when none is. */
  protected volatile OptionalLong blockObserverId = OptionalLong.empty();

  /** Set once {@link #close} has begun. */
  protected volatile boolean closed = false;

  private volatile boolean halted = false;
  private volatile long initialRetryDelayMillis = DEFAULT_INITIAL_RETRY_DELAY_MILLIS;
  private volatile long maxRetryDelayMillis = DEFAULT_MAX_RETRY_DELAY_MILLIS;
  // 0 means no failure in a row; only touched from the catch-up thread.
  private long retryDelayMillis = 0L;

  /**
   * Creates a worker.
   *
   * @param blockchain the blockchain whose new canonical heads advance the target
   * @param executorService the single-threaded executor catch-ups run on; shut down by {@link
   *     #close}
   * @param closeTimeoutSeconds how long {@link #close} waits for an in-flight catch-up
   */
  protected TrailingBlockWorker(
      final Blockchain blockchain,
      final ScheduledExecutorService executorService,
      final long closeTimeoutSeconds) {
    this.blockchain = blockchain;
    this.executorService = executorService;
    this.closeTimeoutSeconds = closeTimeoutSeconds;
  }

  /**
   * The target block for a canonical head.
   *
   * @param headNumber the new canonical head's block number
   * @return the block the worker should trail to
   */
  protected abstract long targetFor(long headNumber);

  /**
   * The highest block the worker has completed.
   *
   * @return the block number
   */
  protected abstract long position();

  /**
   * Does the work needed to move {@link #position} towards {@link #targetBlock}. Runs on the
   * executor, never concurrently with itself. An exception is retried with backoff.
   */
  protected abstract void catchUp();

  /**
   * Whether a catch-up is worth starting after a new head or a finished run. Defaults to whether
   * the worker is behind its target; subclasses can require a larger gap to batch up work.
   *
   * @return true to start a catch-up
   */
  protected boolean shouldCatchUp() {
    return position() < targetBlock.get();
  }

  /**
   * Starts following new canonical heads, raising the target and scheduling a catch-up whenever
   * {@link #shouldCatchUp} says there is work.
   */
  protected final void startObserving() {
    blockObserverId =
        OptionalLong.of(
            blockchain.observeBlockAdded(
                event -> {
                  if (event.isNewCanonicalHead()) {
                    targetBlock.accumulateAndGet(
                        targetFor(event.getHeader().getNumber()), Math::max);
                    if (shouldCatchUp()) {
                      scheduleCatchUpIfNeeded();
                    }
                  }
                }));
  }

  /** Queues a catch-up unless one is already in flight, or the worker is closed or halted. */
  protected final void scheduleCatchUpIfNeeded() {
    if (closed || halted || !catchUpRunning.compareAndSet(false, true)) {
      return;
    }
    try {
      executorService.submit(this::runCatchUp);
    } catch (final RejectedExecutionException e) {
      catchUpRunning.set(false);
      LOG.debug("{} executor shut down; skipping catch-up", getClass().getSimpleName());
    }
  }

  /**
   * Runs {@code task} on the worker's executor, after any in-flight catch-up, and waits for it.
   *
   * @param task the work to run, returning whether it succeeded
   * @return the task's result, or false if it threw, or the worker closed before it ran
   */
  protected final boolean runAndWait(final BooleanSupplier task) {
    if (closed) {
      return false;
    }
    try {
      return executorService.submit(task::getAsBoolean).get();
    } catch (final RejectedExecutionException | CancellationException e) {
      return false;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (final ExecutionException e) {
      LOG.warn("{} task failed", getClass().getSimpleName(), e.getCause());
      return false;
    }
  }

  /** Stops scheduling catch-ups, for a condition that retrying cannot fix. */
  protected final void halt() {
    halted = true;
  }

  private void runCatchUp() {
    boolean failed = false;
    try {
      catchUp();
      retryDelayMillis = 0L;
    } catch (final RuntimeException e) {
      failed = true;
      if (closed) {
        LOG.debug("{} catch-up interrupted by close", getClass().getSimpleName(), e);
      } else {
        LOG.error(
            "{} catch-up failed at block {} (target {})",
            getClass().getSimpleName(),
            position(),
            targetBlock.get(),
            e);
      }
    } finally {
      if (failed && !closed) {
        // Keep catchUpRunning set while backing off so head events cannot bypass the delay.
        scheduleRetry();
      } else {
        catchUpRunning.set(false);
        if (!closed && shouldCatchUp()) {
          scheduleCatchUpIfNeeded();
        }
      }
    }
  }

  private void scheduleRetry() {
    final long delay = retryDelayMillis > 0 ? retryDelayMillis : initialRetryDelayMillis;
    retryDelayMillis = Math.min(delay * 2, maxRetryDelayMillis);
    LOG.debug("{} retrying in {} ms", getClass().getSimpleName(), delay);
    try {
      executorService.schedule(this::runCatchUp, delay, TimeUnit.MILLISECONDS);
    } catch (final RejectedExecutionException e) {
      catchUpRunning.set(false);
      LOG.debug("{} executor shut down; skipping retry", getClass().getSimpleName());
    }
  }

  /**
   * Stops following heads and waits for an in-flight catch-up, so the caller can release what the
   * catch-up uses.
   */
  @Override
  public void close() {
    closed = true;
    blockObserverId.ifPresent(blockchain::removeObserver);
    blockObserverId = OptionalLong.empty();
    // shutdownNow drops queued tasks without completing their futures; cancel them so callers
    // blocked in runAndWait are released instead of waiting forever.
    for (final Runnable dropped : executorService.shutdownNow()) {
      if (dropped instanceof Future<?> future) {
        future.cancel(false);
      }
    }
    try {
      if (!executorService.awaitTermination(closeTimeoutSeconds, TimeUnit.SECONDS)) {
        LOG.warn(
            "{} catch-up did not stop within {}s of close",
            getClass().getSimpleName(),
            closeTimeoutSeconds);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Whether the worker has stopped scheduling catch-ups for good.
   *
   * @return true once halted
   */
  @VisibleForTesting
  public boolean isHalted() {
    return halted;
  }

  /**
   * Whether a catch-up is queued, running or backing off.
   *
   * @return true while one is in flight
   */
  @VisibleForTesting
  public boolean isCatchUpRunning() {
    return catchUpRunning.get();
  }

  /**
   * The registered block observer.
   *
   * @return the observer id, or empty if none is registered
   */
  @VisibleForTesting
  public OptionalLong observerId() {
    return blockObserverId;
  }

  /**
   * The current target block.
   *
   * @return the highest block the worker should reach
   */
  @VisibleForTesting
  public long targetBlock() {
    return targetBlock.get();
  }

  /**
   * Overrides the retry backoff so tests need not wait for the defaults.
   *
   * @param initialMillis delay before the first retry
   * @param maxMillis cap on the doubling delay
   */
  @VisibleForTesting
  public void setRetryDelaysMillis(final long initialMillis, final long maxMillis) {
    this.initialRetryDelayMillis = initialMillis;
    this.maxRetryDelayMillis = maxMillis;
  }
}
