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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

/**
 * Registry for managing backfill tasks.
 *
 * <p>This class allows for registering, unregistering, and managing the state of backfill tasks.
 */
@Singleton
public class BackfillRegistry {
  private final Set<BackfillTask> tasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

  /** Default constructor for BackfillRegistry. */
  public BackfillRegistry() {}

  /**
   * Registers a backfill task.
   *
   * @param task the backfill task to register
   */
  public void register(final BackfillTask task) {
    tasks.add(task);
  }

  /**
   * Unregisters a backfill task.
   *
   * @param task the backfill task to unregister
   */
  public void unregister(final BackfillTask task) {
    tasks.remove(task);
  }

  /**
   * Returns if any backfill tasks are currently running.
   *
   * @return true if any task is running, false otherwise
   */
  public boolean anyRunning() {
    return tasks.stream().anyMatch(BackfillTask::isRunning);
  }

  /** Requests all backfill tasks to pause. */
  public void pauseAll() {
    tasks.forEach(BackfillTask::requestPause);
  }

  /**
   * Stops all backfill tasks.
   *
   * <p>This method will stop all tasks that are currently registered.
   */
  public void stopAll() {
    tasks.forEach(BackfillTask::stop);
  }

  /**
   * Awaits until all backfill tasks are paused.
   *
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public void awaitAllPaused() throws InterruptedException {
    for (BackfillTask task : tasks) {
      task.awaitPaused();
    }
  }

  /**
   * Resumes all paused backfill tasks.
   *
   * <p>This method will resume all tasks that are currently paused.
   */
  public void resumeAll() {
    tasks.forEach(BackfillTask::resume);
  }
}
