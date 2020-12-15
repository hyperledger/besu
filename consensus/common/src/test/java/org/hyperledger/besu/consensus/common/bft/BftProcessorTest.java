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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class BftProcessorTest {
  private EventMultiplexer mockeEventMultiplexer;

  @Before
  public void initialise() {
    mockeEventMultiplexer = mock(EventMultiplexer.class);
  }

  @Test
  public void handlesStopGracefully() throws InterruptedException {
    final BftEventQueue mockQueue = mock(BftEventQueue.class);
    Mockito.when(mockQueue.poll(anyLong(), any())).thenReturn(null);
    final BftProcessor processor = new BftProcessor(mockQueue, mockeEventMultiplexer);

    // Start the BftProcessor
    final ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
    final Future<?> processorFuture = processorExecutor.submit(processor);

    // Make sure we've hit the queue at least once
    verify(mockQueue, timeout(3000).atLeastOnce()).poll(anyLong(), any());

    // Instruct the processor to stop
    processor.stop();

    // Executor shutdown should wait for the processor to gracefully exit
    processorExecutor.shutdown();
    // If it hasn't within 200 ms then something will be wrong
    final boolean executorCompleted =
        processorExecutor.awaitTermination(2000, TimeUnit.MILLISECONDS);
    assertThat(executorCompleted).isTrue();

    // The processor task has exited
    assertThat(processorFuture.isDone()).isTrue();
  }

  @Test
  public void cleanupExecutorsAfterShutdownNow() throws InterruptedException {
    final BftProcessor processor = new BftProcessor(new BftEventQueue(1000), mockeEventMultiplexer);

    // Start the BftProcessor
    final ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
    final Future<?> processorFuture = processorExecutor.submit(processor);

    // Instruct the processor to stop
    processor.stop();

    // Executor shutdown should interrupt the processor
    processorExecutor.shutdownNow();
    // If it hasn't within 200 ms then something will be wrong
    final boolean executorCompleted =
        processorExecutor.awaitTermination(2000, TimeUnit.MILLISECONDS);
    assertThat(executorCompleted).isTrue();

    // The processor task has exited
    assertThat(processorFuture.isDone()).isTrue();
  }

  @Test
  public void handlesQueueInterruptGracefully() throws InterruptedException {
    // Setup a queue that will always interrupt
    final BftEventQueue mockQueue = mock(BftEventQueue.class);
    Mockito.when(mockQueue.poll(anyLong(), any())).thenThrow(new InterruptedException());

    final BftProcessor processor = new BftProcessor(mockQueue, mockeEventMultiplexer);

    // Start the BftProcessor
    final ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
    final Future<?> processorFuture = processorExecutor.submit(processor);

    // Make sure we've hit the queue at least once
    verify(mockQueue, timeout(3000).atLeastOnce()).poll(anyLong(), any());

    // Executor shutdown should wait for the processor to gracefully exit
    processorExecutor.shutdown();

    // The processor task hasn't exited off the back of the interrupts
    assertThat(processorFuture.isDone()).isFalse();

    processor.stop();

    // If it hasn't within 200 ms then something will be wrong and we're not waking up
    final boolean executorCompleted =
        processorExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
    assertThat(executorCompleted).isTrue();

    // The processor task has woken up and exited
    assertThat(processorFuture.isDone()).isTrue();
  }

  @Test
  public void drainEventsIntoStateMachine() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(1000);
    final BftProcessor processor = new BftProcessor(queue, mockeEventMultiplexer);

    // Start the BftProcessor
    final ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
    processorExecutor.execute(processor);

    final RoundExpiry roundExpiryEvent = new RoundExpiry(new ConsensusRoundIdentifier(1, 1));

    queue.add(roundExpiryEvent);
    queue.add(roundExpiryEvent);

    Awaitility.await().atMost(3000, TimeUnit.MILLISECONDS).until(queue::isEmpty);

    processor.stop();
    processorExecutor.shutdown();

    verify(mockeEventMultiplexer, times(2)).handleBftEvent(eq(roundExpiryEvent));
  }
}
