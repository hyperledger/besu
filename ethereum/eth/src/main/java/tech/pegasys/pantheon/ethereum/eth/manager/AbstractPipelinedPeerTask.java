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
package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.OperationTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractPipelinedPeerTask<I, O> extends AbstractPeerTask<List<O>> {
  private static final Logger LOG = LogManager.getLogger();

  static final int TIMEOUT_MS = 1000;

  private BlockingQueue<I> inboundQueue;
  private BlockingQueue<O> outboundQueue;
  private List<O> results;

  private boolean shuttingDown = false;
  private AtomicReference<Throwable> processingException = new AtomicReference<>(null);

  protected AbstractPipelinedPeerTask(
      final BlockingQueue<I> inboundQueue,
      final int outboundBacklogSize,
      final EthContext ethContext,
      final LabelledMetric<OperationTimer> ethTasksTimer) {
    super(ethContext, ethTasksTimer);
    this.inboundQueue = inboundQueue;
    outboundQueue = new LinkedBlockingQueue<>(outboundBacklogSize);
    results = new ArrayList<>();
  }

  @Override
  protected void executeTaskWithPeer(final EthPeer peer) {
    Optional<I> previousInput = Optional.empty();
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
      } catch (final InterruptedException e) {
        // this is expected
        continue;
      }
      final Optional<O> output = processStep(input, previousInput, peer);
      output.ifPresent(
          o -> {
            try {
              outboundQueue.put(o);
            } catch (final InterruptedException e) {
              processingException.compareAndSet(null, e);
            }
            results.add(o);
          });
      previousInput = Optional.of(input);
    }
    if (processingException.get() == null) {
      result.get().complete(new PeerTaskResult<>(peer, results));
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

  protected abstract Optional<O> processStep(I input, Optional<I> previousInput, EthPeer peer);
}
