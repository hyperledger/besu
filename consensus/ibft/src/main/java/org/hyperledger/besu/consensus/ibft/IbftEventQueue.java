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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Threadsafe queue that lets parts of the system inform the Ibft infrastructure about events */
public class IbftEventQueue {
  private final BlockingQueue<IbftEvent> queue = new LinkedBlockingQueue<>();

  private static final Logger LOG = LogManager.getLogger();
  private final int messageQueueLimit;

  public IbftEventQueue(final int messageQueueLimit) {
    this.messageQueueLimit = messageQueueLimit;
  }

  /**
   * Put an Ibft event onto the queue
   *
   * @param event Provided ibft event
   */
  public void add(final IbftEvent event) {
    if (queue.size() > messageQueueLimit) {
      LOG.warn("Queue size exceeded trying to add new ibft event {}", event);
    } else {
      queue.add(event);
    }
  }

  public int size() {
    return queue.size();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  /**
   * Blocking request for the next item available on the queue that will timeout after a specified
   * period
   *
   * @param timeout number of time units after which this operation should timeout
   * @param unit the time units in which to count
   * @return The next IbftEvent to become available on the queue or null if the expiry passes
   * @throws InterruptedException If the underlying queue implementation is interrupted
   */
  @Nullable
  public IbftEvent poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    return queue.poll(timeout, unit);
  }
}
