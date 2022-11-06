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

import org.hyperledger.besu.config.BftConfigOptions;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class for starting and keeping organised block timers */
public class BlockTimer {

  private static final Logger LOG = LoggerFactory.getLogger(BlockTimer.class);

  private final ForksSchedule<? extends BftConfigOptions> forksSchedule;
  private final BftExecutors bftExecutors;
  private Optional<ScheduledFuture<?>> currentTimerTask;
  private final BftEventQueue queue;
  private final Clock clock;

  /**
   * Construct a BlockTimer with primed executor service ready to start timers
   *
   * @param queue The queue in which to put block expiry events
   * @param forksSchedule Bft fork schedule that contains block period seconds
   * @param bftExecutors Executor services that timers can be scheduled with
   * @param clock System clock
   */
  public BlockTimer(
      final BftEventQueue queue,
      final ForksSchedule<? extends BftConfigOptions> forksSchedule,
      final BftExecutors bftExecutors,
      final Clock clock) {
    this.queue = queue;
    this.forksSchedule = forksSchedule;
    this.bftExecutors = bftExecutors;
    this.currentTimerTask = Optional.empty();
    this.clock = clock;
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
   * Starts a timer for the supplied round cancelling any previously active block timer
   *
   * @param round The round identifier which this timer is tracking
   * @param chainHeadHeader The header of the chain head
   */
  public synchronized void startTimer(
      final ConsensusRoundIdentifier round, final BlockHeader chainHeadHeader) {
    cancelTimer();

    final long now = clock.millis();

    // absolute time when the timer is supposed to expire
    final int blockPeriodSeconds =
        forksSchedule.getFork(round.getSequenceNumber()).getValue().getBlockPeriodSeconds();
    final long minimumTimeBetweenBlocksMillis = blockPeriodSeconds * 1000L;
    final long expiryTime = chainHeadHeader.getTimestamp() * 1_000 + minimumTimeBetweenBlocksMillis;

    System.err.println(
        "*** TODO SLD | BlockTimer.startTimer() | cancelling existing timer and submitting new timerTask with expiryTime = "
            + expiryTime);

    if (expiryTime > now) {
      final long delay = expiryTime - now;

      final Runnable newTimerRunnable = () -> queue.add(new BlockTimerExpiry(round));

      final ScheduledFuture<?> newTimerTask =
          bftExecutors.scheduleTask(newTimerRunnable, delay, TimeUnit.MILLISECONDS);
      currentTimerTask = Optional.of(newTimerTask);
    } else {
      queue.add(new BlockTimerExpiry(round));
    }
  }

  public synchronized void startTimer(
      final ConsensusRoundIdentifier round,
      final BlockHeader chainHeadHeader,
      final long delayDuringEmptyBlockPeriod) {
    cancelTimer();

    final long now = clock.millis();

    // absolute time when the timer is supposed to expire
    final int blockPeriodSeconds =
        forksSchedule.getFork(round.getSequenceNumber()).getValue().getBlockPeriodSeconds();
    final long minimumTimeBetweenBlocksMillis =
        blockPeriodSeconds * 1000L + delayDuringEmptyBlockPeriod;
    final long expiryTime = chainHeadHeader.getTimestamp() * 1_000 + minimumTimeBetweenBlocksMillis;

    LOG.info(
        "*** TODO SLD | BlockTimer.startTimer() | cancelling existing timer and submitting new timerTask with expiryTime = {} (extra delay of {})",
        Instant.ofEpochMilli(expiryTime).atZone(ZoneId.systemDefault()),
        delayDuringEmptyBlockPeriod);

    if (expiryTime > now) {
      final long delay = expiryTime - now;

      final Runnable newTimerRunnable =
          () -> {
            LOG.info("TODO SLD BlockTimer expired -> new BlockTimerExpiry({})", round);
            queue.add(new BlockTimerExpiry(round));
          };

      final ScheduledFuture<?> newTimerTask =
          bftExecutors.scheduleTask(newTimerRunnable, delay, TimeUnit.MILLISECONDS);
      currentTimerTask = Optional.of(newTimerTask);
    } else {
      queue.add(new BlockTimerExpiry(round));
    }
  }
}
