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
package tech.pegasys.pantheon.services.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class AsyncOperationProcessor<I, O> implements Processor<I, O> {
  private static final Logger LOG = LogManager.getLogger();
  private final Function<I, CompletableFuture<O>> processor;
  private final Collection<CompletableFuture<O>> inProgress;
  private final int maxConcurrency;

  public AsyncOperationProcessor(
      final Function<I, CompletableFuture<O>> processor, final int maxConcurrency) {
    this.processor = processor;
    this.maxConcurrency = maxConcurrency;
    this.inProgress = new ArrayList<>(maxConcurrency);
  }

  @Override
  public void processNextInput(final ReadPipe<I> inputPipe, final WritePipe<O> outputPipe) {
    if (inProgress.size() < maxConcurrency) {
      final I value = inputPipe.get();
      if (value != null) {
        final CompletableFuture<O> future = processor.apply(value);
        // When the future completes, interrupt so if we're waiting for new input we wake up and
        // schedule the output.
        final Thread stageThread = Thread.currentThread();
        inProgress.add(future);
        future.whenComplete((result, error) -> stageThread.interrupt());
      }
      outputCompletedTasks(outputPipe);
    } else {
      outputNextCompletedTask(outputPipe);
    }
  }

  @Override
  public void finalize(final WritePipe<O> outputPipe) {
    while (!inProgress.isEmpty()) {
      outputNextCompletedTask(outputPipe);
    }
  }

  @Override
  public void abort() {
    inProgress.forEach(future -> future.cancel(true));
  }

  private void outputNextCompletedTask(final WritePipe<O> outputPipe) {
    try {
      waitForAnyFutureToComplete();
      outputCompletedTasks(outputPipe);
    } catch (final InterruptedException e) {
      LOG.trace("Interrupted while waiting for processing to complete", e);
    } catch (final ExecutionException e) {
      LOG.error("Processing failed and we don't handle exceptions properly yet", e);
    } catch (final TimeoutException e) {
      // Ignore and go back around the loop.
    }
  }

  @SuppressWarnings("rawtypes")
  private void waitForAnyFutureToComplete()
      throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture.anyOf(inProgress.toArray(new CompletableFuture[0])).get(1, TimeUnit.SECONDS);
  }

  private void outputCompletedTasks(final WritePipe<O> outputPipe) {
    for (final Iterator<CompletableFuture<O>> i = inProgress.iterator(); i.hasNext(); ) {
      final CompletableFuture<O> process = i.next();
      final O result = process.getNow(null);
      if (result != null) {
        outputPipe.put(result);
        i.remove();
      }
    }
  }
}
