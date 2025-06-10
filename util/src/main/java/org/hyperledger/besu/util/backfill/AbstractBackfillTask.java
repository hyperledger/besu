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
package org.hyperledger.besu.util.backfill;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract base class for backfill tasks that provides common functionality such as starting,
 * pausing, resuming, and stopping the task.
 */
public abstract class AbstractBackfillTask implements BackfillTask {
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final ReentrantLock pauseLock = new ReentrantLock();
  private final Condition pauseCondition = pauseLock.newCondition();

  private Thread workerThread;

  private final BackfillRegistry backfillRegistry;

  /**
   * Constructs an AbstractBackfillTask with the specified BackfillRegistry.
   *
   * @param backfillRegistry the registry to register this task with
   */
  public AbstractBackfillTask(final BackfillRegistry backfillRegistry) {
    this.backfillRegistry = backfillRegistry;
  }

  @Override
  public void start() {
    if (running.compareAndSet(false, true)) {
      workerThread = new Thread(this::run);
      workerThread.start();
      backfillRegistry.register(this);
    }
  }

  @Override
  public void requestPause() {
    paused.set(true);
  }

  @Override
  public void awaitPaused() throws InterruptedException {
    pauseLock.lock();
    try {
      while (running.get() && !isPaused()) {
        pauseCondition.await();
      }
    } finally {
      pauseLock.unlock();
    }
  }

  @Override
  public void resume() {
    paused.set(false);
    pauseLock.lock();
    try {
      pauseCondition.signalAll();
    } finally {
      pauseLock.unlock();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isPaused() {
    return paused.get();
  }

  /**
   * The unit of work to be executed by this backfill task. This method should contain the logic for
   * processing one unit of work of the backfill.
   */
  protected abstract void executeUnitOfWork();

  private void run() {
    while (running.get()) {
      pauseLock.lock();
      try {
        while (paused.get()) {
          pauseCondition.signalAll(); // inform awaitPaused()
          pauseCondition.await();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } finally {
        pauseLock.unlock();
      }

      executeUnitOfWork();
    }
  }

  /** Stops the backfill task, marking it as not running and unregistering it from the registry. */
  @Override
  public void stop() {
    running.set(false);
    wakeUpIfPaused(); // unblock any await in checkPause or sleepInterruptibly
    try {
      if (workerThread != null) {
        workerThread.join();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    backfillRegistry.unregister(this);
  }

  /**
   * Sleeps for the specified duration, allowing for interruption and respecting the paused state.
   *
   * @param millis the duration to sleep in milliseconds
   * @throws InterruptedException if the thread is interrupted while sleeping
   */
  protected void sleepInterruptibly(final long millis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + millis;
    while (true) {
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        return;
      }

      pauseLock.lock();
      try {
        if (paused.get()) {
          pauseCondition.signalAll();
          while (paused.get()) {
            pauseCondition.await();
          }
        } else {
          pauseCondition.awaitNanos(remaining * 1_000_000);
        }
      } finally {
        pauseLock.unlock();
      }
    }
  }

  private void wakeUpIfPaused() {
    pauseLock.lock();
    try {
      pauseCondition.signalAll();
    } finally {
      pauseLock.unlock();
    }
  }
}
