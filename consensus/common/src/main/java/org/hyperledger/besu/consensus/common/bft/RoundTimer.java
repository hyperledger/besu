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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class for starting and keeping organised round timers */
public class RoundTimer {

  private static final Logger LOG = LoggerFactory.getLogger(RoundTimer.class);

  private final BftExecutors bftExecutors;
  private Optional<ScheduledFuture<?>> currentTimerTask;
  private final BftEventQueue queue;
  private final Duration baseExpiryPeriod;

  /**
   * Construct a RoundTimer with primed executor service ready to start timers
   *
   * @param queue The queue in which to put round expiry events
   * @param baseExpiryPeriod The initial round length for round 0
   * @param bftExecutors executor service that timers can be scheduled with
   */
  public RoundTimer(
      final BftEventQueue queue, final Duration baseExpiryPeriod, final BftExecutors bftExecutors) {
    this.queue = queue;
    this.bftExecutors = bftExecutors;
    this.currentTimerTask = Optional.empty();
    this.baseExpiryPeriod = baseExpiryPeriod;
  }

  /** Cancels the current running round timer if there is one */
  public synchronized void cancelTimer() {
    currentTimerTask.ifPresent(t -> t.cancel(false));
    currentTimerTask = Optional.empty();
  }

  /**
   * Whether there is a timer currently running or not
   *
   * @return boolean of whether a timer is ticking or not
   */
  public synchronized boolean isRunning() {
    return currentTimerTask.map(t -> !t.isDone()).orElse(false);
  }

  /**
   * Starts a timer for the supplied round cancelling any previously active round timer
   *
   * @param round The round identifier which this timer is tracking
   */
  public synchronized void startTimer(final ConsensusRoundIdentifier round) {
    cancelTimer();

    final long expiryTime =
        baseExpiryPeriod.toMillis() * (long) Math.pow(2, round.getRoundNumber());

    final Runnable newTimerRunnable = () -> queue.add(new RoundExpiry(round));

    final ScheduledFuture<?> newTimerTask =
        bftExecutors.scheduleTask(newTimerRunnable, expiryTime, TimeUnit.MILLISECONDS);

    // Once we are up to round 2 start logging round expiries
    if (round.getRoundNumber() >= 2) {
      LOG.info(
          "BFT round {} expired. Moved to round {} which will expire in {} seconds",
          round.getRoundNumber() - 1,
          round.getRoundNumber(),
          (expiryTime / 1000));
    }

    currentTimerTask = Optional.of(newTimerTask);
  }
}
