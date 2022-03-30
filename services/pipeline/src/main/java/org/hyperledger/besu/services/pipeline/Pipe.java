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
package org.hyperledger.besu.services.pipeline;

import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forms the connection between two pipeline stages. A pipe is essentially a blocking queue with the
 * added ability to signal when no further input is available because the pipe has been closed or
 * the pipeline aborted.
 *
 * <p>In most cases a Pipe is used through one of two narrower interfaces it supports {@link
 * ReadPipe} and {@link WritePipe}. These are designed to expose only the operations relevant to
 * objects either reading from or publishing to the pipe respectively.
 *
 * @param <T> the type of item that flows through the pipe.
 */
public class Pipe<T> implements ReadPipe<T>, WritePipe<T> {
  private static final Logger LOG = LoggerFactory.getLogger(Pipe.class);
  private final BlockingQueue<T> queue;
  private final Counter inputCounter;
  private final Counter outputCounter;
  private final Counter abortedItemCounter;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean aborted = new AtomicBoolean();

  public Pipe(
      final int capacity,
      final Counter inputCounter,
      final Counter outputCounter,
      final Counter abortedItemCounter) {
    queue = new ArrayBlockingQueue<>(capacity);
    this.inputCounter = inputCounter;
    this.outputCounter = outputCounter;
    this.abortedItemCounter = abortedItemCounter;
  }

  @Override
  public boolean isOpen() {
    return !closed.get() && !aborted.get();
  }

  @Override
  public boolean isAborted() {
    return aborted.get();
  }

  @Override
  public boolean hasRemainingCapacity() {
    return queue.remainingCapacity() > 0 && isOpen();
  }

  @Override
  public void close() {
    closed.set(true);
  }

  @Override
  public void abort() {
    if (aborted.compareAndSet(false, true)) {
      abortedItemCounter.inc(queue.size());
    }
  }

  @Override
  public boolean hasMore() {
    if (aborted.get()) {
      return false;
    }
    return !closed.get() || !queue.isEmpty();
  }

  @Override
  public T get() {
    try {
      while (hasMore()) {
        final T value = queue.poll(1, TimeUnit.SECONDS);
        if (value != null) {
          outputCounter.inc();
          return value;
        }
      }
    } catch (final InterruptedException e) {
      LOG.trace("Interrupted while waiting for next item", e);
    }
    return null;
  }

  @Override
  public T poll() {
    final T item = queue.poll();
    if (item != null) {
      outputCounter.inc();
    }
    return item;
  }

  @Override
  public int drainTo(final Collection<T> output, final int maxElements) {
    final int count = queue.drainTo(output, maxElements);
    outputCounter.inc(count);
    return count;
  }

  @Override
  public void put(final T value) {
    while (isOpen()) {
      try {
        if (queue.offer(value, 1, TimeUnit.SECONDS)) {
          inputCounter.inc();
          return;
        }
      } catch (final InterruptedException e) {
        LOG.trace("Interrupted while waiting to add to output", e);
      }
    }
  }
}
