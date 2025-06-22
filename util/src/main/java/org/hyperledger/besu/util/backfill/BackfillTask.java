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

/**
 * Interface representing a backfill task that can be started, paused, resumed, and queried for its
 * state.
 */
public interface BackfillTask {
  /** Starts the backfill task. */
  void start();

  /** Requests the backfill task to pause. */
  void requestPause();

  /**
   * Awaits until the backfill task is paused.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  void awaitPaused() throws InterruptedException;

  /** Resumes the backfill task if it is paused. */
  void resume();

  /**
   * Checks if the backfill task is currently running.
   *
   * @return true if the task is running, false otherwise.
   */
  boolean isRunning();

  /**
   * Checks if the backfill task is currently paused.
   *
   * @return true if the task is paused, false otherwise.
   */
  boolean isPaused();

  /** Stops the backfill task. */
  void stop();
}
