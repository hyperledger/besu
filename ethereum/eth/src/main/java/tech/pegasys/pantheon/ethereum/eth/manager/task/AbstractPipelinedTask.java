/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.eth.manager.task;

import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractPipelinedTask<I, O> extends AbstractEthTask<List<O>> {
  private static final Logger LOG = LogManager.getLogger();

  static final int TIMEOUT_MS = 1000;

  private final BlockingQueue<I> inboundQueue;
  private final BlockingQueue<O> outboundQueue;
  private final List<O> results;

  private boolean shuttingDown = false;
  private final AtomicReference<Throwable> processingException = new AtomicReference<>(null);

  private final Counter inboundQueueCounter;
  private final Counter outboundQueueCounter;

  protected AbstractPipelinedTask(
      final BlockingQueue<I> inboundQueue,
      final int outboundBacklogSize,
      final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.inboundQueue = inboundQueue;
    outboundQueue = new LinkedBlockingQueue<>(outboundBacklogSize);
    results = new ArrayList<>();
    this.inboundQueueCounter =
        metricsSystem.createCounter(
            MetricCategory.SYNCHRONIZER,
            "inboundQueueCounter",
            "count of queue items that started processing");
    this.outboundQueueCounter =
        metricsSystem.createCounter(
            MetricCategory.SYNCHRONIZER,
            "outboundQueueCounter",
            "count of queue items that finished processing");
  }

  @Override
  protected void executeTask() {
    Optional<I> previousInput = Optional.empty();
    try {
      while (!isDone() && processingException.get() == null) {
        if (shuttingDown && inboundQueue.isEmpty()) {
          break;
        }
        final I input;
        try {
          input = inboundQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
          if (input == null) {
            // timed out waiting for a result
            continue;
          }
          inboundQueueCounter.inc();
        } catch (final InterruptedException e) {
          // this is expected
          continue;
        }
        final Optional<O> output = processStep(input, previousInput);
        output.ifPresent(
            o -> {
              while (!isDone()) {
                try {
                  if (outboundQueue.offer(o, 1, TimeUnit.SECONDS)) {
                    outboundQueueCounter.inc();
                    results.add(o);
                    break;
                  }
                } catch (final InterruptedException e) {
                  processingException.compareAndSet(null, e);
                  break;
                }
              }
            });
        previousInput = Optional.of(input);
      }
    } catch (final RuntimeException e) {
      processingException.compareAndSet(null, e);
    }
    if (processingException.get() == null) {
      result.get().complete(results);
    } else {
      result.get().completeExceptionally(processingException.get());
    }
  }

  public BlockingQueue<O> getOutboundQueue() {
    return outboundQueue;
  }

  public void shutdown() {
    this.shuttingDown = true;
  }

  protected void failExceptionally(final Throwable t) {
    if (!(t instanceof InterruptedException)) {
      LOG.error("Task Failure", t);
    }
    processingException.compareAndSet(null, t);
    result.get().completeExceptionally(t);
    cancel();
  }

  protected abstract Optional<O> processStep(I input, Optional<I> previousInput);
}
