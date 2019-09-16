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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.ibft.ibftevent.IbftEvent;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Execution context for draining queued ibft events and applying them to a maintained state */
public class IbftProcessor implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  private final IbftEventQueue incomingQueue;
  private volatile boolean shutdown = false;
  private final EventMultiplexer eventMultiplexer;

  /**
   * Construct a new IbftProcessor
   *
   * @param incomingQueue The event queue from which to drain new events
   * @param eventMultiplexer an object capable of handling any/all IBFT events
   */
  public IbftProcessor(
      final IbftEventQueue incomingQueue, final EventMultiplexer eventMultiplexer) {
    this.incomingQueue = incomingQueue;
    this.eventMultiplexer = eventMultiplexer;
  }

  /** Indicate to the processor that it should gracefully stop at its next opportunity */
  public void stop() {
    shutdown = true;
  }

  @Override
  public void run() {
    try {
      while (!shutdown) {
        nextIbftEvent().ifPresent(eventMultiplexer::handleIbftEvent);
      }
    } catch (final Throwable t) {
      LOG.error("IBFT Mining thread has suffered a fatal error, mining has been halted", t);
    }
    // Clean up the executor service the round timer has been utilising
    LOG.info("Shutting down IBFT event processor");
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
