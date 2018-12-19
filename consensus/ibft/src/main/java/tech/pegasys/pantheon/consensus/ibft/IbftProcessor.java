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
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.statemachine.IbftController;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Execution context for draining queued ibft events and applying them to a maintained state */
public class IbftProcessor implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  private final IbftEventQueue incomingQueue;
  private final ScheduledExecutorService roundTimerExecutor;
  private volatile boolean shutdown = false;
  private final IbftController ibftController;

  /**
   * Construct a new IbftProcessor
   *
   * @param incomingQueue The event queue from which to drain new events
   * @param ibftController an object capable of handling any/all IBFT events
   */
  public IbftProcessor(final IbftEventQueue incomingQueue, final IbftController ibftController) {
    // Spawning the round timer with a single thread as we should never have more than 1 timer in
    // flight at a time
    this(incomingQueue, ibftController, Executors.newSingleThreadScheduledExecutor());
  }

  @VisibleForTesting
  IbftProcessor(
      final IbftEventQueue incomingQueue,
      final IbftController ibftController,
      final ScheduledExecutorService roundTimerExecutor) {
    this.incomingQueue = incomingQueue;
    this.ibftController = ibftController;
    this.roundTimerExecutor = roundTimerExecutor;
  }

  public void start() {
    ibftController.start();
  }

  /** Indicate to the processor that it should gracefully stop at its next opportunity */
  public void stop() {
    shutdown = true;
  }

  @Override
  public void run() {
    while (!shutdown) {
      nextIbftEvent().ifPresent(this::handleIbftEvent);
    }
    // Clean up the executor service the round timer has been utilising
    roundTimerExecutor.shutdownNow();
  }

  private void handleIbftEvent(final IbftEvent ibftEvent) {
    try {
      switch (ibftEvent.getType()) {
        case MESSAGE:
          final IbftReceivedMessageEvent rxEvent = (IbftReceivedMessageEvent) ibftEvent;
          ibftController.handleMessageEvent(rxEvent);
          break;
        case ROUND_EXPIRY:
          final RoundExpiry roundExpiryEvent = (RoundExpiry) ibftEvent;
          ibftController.handleRoundExpiry(roundExpiryEvent);
          break;
        case NEW_CHAIN_HEAD:
          final NewChainHead newChainHead = (NewChainHead) ibftEvent;
          ibftController.handleNewBlockEvent(newChainHead);
          break;
        case BLOCK_TIMER_EXPIRY:
          final BlockTimerExpiry blockTimerExpiry = (BlockTimerExpiry) ibftEvent;
          ibftController.handleBlockTimerExpiry(blockTimerExpiry);
          break;
        default:
          throw new RuntimeException("Illegal event in queue.");
      }
    } catch (final Exception e) {
      LOG.error("State machine threw exception while processing event {" + ibftEvent + "}", e);
    }
  }

  private Optional<IbftEvent> nextIbftEvent() {
    try {
      return Optional.ofNullable(incomingQueue.poll(500, TimeUnit.MILLISECONDS));
    } catch (final InterruptedException interrupt) {
      // If the queue was interrupted propagate it and spin to check our shutdown status
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
