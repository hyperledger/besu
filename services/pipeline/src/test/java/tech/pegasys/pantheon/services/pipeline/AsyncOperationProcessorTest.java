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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.Test;

public class AsyncOperationProcessorTest {

  @SuppressWarnings("unchecked")
  private final ReadPipe<CompletableFuture<String>> readPipe = mock(ReadPipe.class);

  @SuppressWarnings("unchecked")
  private final WritePipe<String> writePipe = mock(WritePipe.class);

  private final AsyncOperationProcessor<CompletableFuture<String>, String> processor =
      new AsyncOperationProcessor<>(Function.identity(), 3);

  @Test
  public void shouldImmediatelyOutputTasksThatAreAlreadyCompleteEvenIfOutputPipeIsFull() {
    when(writePipe.hasRemainingCapacity()).thenReturn(false);
    when(readPipe.get()).thenReturn(completedFuture("a"));

    processor.processNextInput(readPipe, writePipe);
    verify(writePipe).put("a");
  }

  @Test
  public void shouldNotExceedConcurrentJobLimit() {

    final CompletableFuture<String> task1 = new CompletableFuture<>();
    final CompletableFuture<String> task2 = new CompletableFuture<>();
    final CompletableFuture<String> task3 = new CompletableFuture<>();
    final CompletableFuture<String> task4 = new CompletableFuture<>();
    when(readPipe.get()).thenReturn(task1).thenReturn(task2).thenReturn(task3).thenReturn(task4);

    // 3 tasks started
    processor.processNextInput(readPipe, writePipe);
    processor.processNextInput(readPipe, writePipe);
    processor.processNextInput(readPipe, writePipe);
    verify(readPipe, times(3)).get();

    // Reached limit of concurrent tasks so this round does nothing.
    processor.processNextInput(readPipe, writePipe);
    verify(readPipe, times(3)).get();

    task1.complete("a");

    // Next round will output the completed task
    processor.processNextInput(readPipe, writePipe);
    verify(writePipe).put("a");

    // And so now we are able to start another one.
    processor.processNextInput(readPipe, writePipe);
    verify(readPipe, times(4)).get();
  }

  @Test
  public void shouldOutputRemainingInProgressTasksWhenFinalizing() {
    final CompletableFuture<String> task1 = new CompletableFuture<>();
    final CompletableFuture<String> task2 = new CompletableFuture<>();
    when(readPipe.get()).thenReturn(task1).thenReturn(task2);

    // Start the two tasks
    processor.processNextInput(readPipe, writePipe);
    processor.processNextInput(readPipe, writePipe);
    verify(readPipe, times(2)).get();
    verifyZeroInteractions(writePipe);

    task1.complete("a");
    task2.complete("b");

    // Processing
    processor.finalize(writePipe);
    verify(writePipe).put("a");
    verify(writePipe).put("b");
  }

  @Test
  public void shouldInterruptThreadWhenFutureCompletes() {
    // Ensures that if we're waiting for the next input we wake up and output completed tasks

    final CompletableFuture<String> task1 = new CompletableFuture<>();
    when(readPipe.get()).thenReturn(task1);

    // Start the two tasks
    processor.processNextInput(readPipe, writePipe);

    task1.complete("a");

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }
}
