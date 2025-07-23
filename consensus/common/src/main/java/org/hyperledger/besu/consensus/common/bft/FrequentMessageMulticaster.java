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

import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Frequent message multicaster. */
public class FrequentMessageMulticaster implements ValidatorMulticaster {
  private final ValidatorMulticaster multicaster;
  private final long interval;
  private final ScheduledExecutorService scheduledExecutorService;
  private volatile ScheduledFuture<?> scheduledTask;
  private static final Logger LOG = LoggerFactory.getLogger(FrequentMessageMulticaster.class);

  /**
   * Constructor that specifies the interval at which to multicast messages
   *
   * @param multicaster Network connections to the remote validators
   * @param multicastInterval Interval at which to multicast messages
   */
  public FrequentMessageMulticaster(
      final ValidatorMulticaster multicaster, final long multicastInterval) {
    this.multicaster = multicaster;
    this.interval = multicastInterval;
    this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  }

  /**
   * Instantiates a new Frequent message multicaster.
   *
   * @param multicaster the multicaster
   */
  @VisibleForTesting
  public FrequentMessageMulticaster(final ValidatorMulticaster multicaster) {
    this(multicaster, 5000);
  }

  private Runnable createPeriodicMulticastTask(
      final MessageData message, final Collection<Address> denylist) {
    return () -> {
      LOG.debug(
          "Broadcasting message every {} ms on thread {}",
          interval,
          Thread.currentThread().threadId());
      multicaster.send(message, denylist);
    };
  }

  @Override
  public synchronized void send(final MessageData message) {
    send(message, Collections.emptyList());
  }

  @Override
  public synchronized void send(final MessageData message, final Collection<Address> denylist) {
    // Cancel the existing task before scheduling a new one
    stopFrequentMulticasting();

    // Define the periodic multicast task
    final Runnable periodicMulticast = createPeriodicMulticastTask(message, denylist);

    // Schedule the periodic task
    scheduledTask =
        scheduledExecutorService.scheduleAtFixedRate(
            periodicMulticast, 0, interval, TimeUnit.MILLISECONDS);

    LOG.debug("Scheduled new frequent multicast task for message.");
  }

  /**
   * Stop frequent multicasting.
   *
   * <p>Stops the current scheduled task.
   */
  public synchronized void stopFrequentMulticasting() {
    if (scheduledTask != null) {
      LOG.debug("Cancelling existing frequent multicast task.");
      scheduledTask.cancel(true);
      scheduledTask = null;
    }
  }
}
