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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;
import static org.hyperledger.besu.util.FutureUtils.propagateCancellation;
import static org.hyperledger.besu.util.FutureUtils.propagateResult;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class FutureUtilsTest {

  private static final RuntimeException ERROR = new RuntimeException("Oh no!");

  @Test
  public void shouldCreateExceptionallyCompletedFuture() {
    final CompletableFuture<Void> future = CompletableFuture.failedFuture(ERROR);
    assertCompletedExceptionally(future, ERROR);
  }

  @Test
  public void shouldPropagateSuccessfulResult() {
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> output = new CompletableFuture<>();
    propagateResult(input, output);
    assertThat(output).isNotDone();

    input.complete("Yay");

    assertThat(output).isCompletedWithValue("Yay");
  }

  @Test
  public void shouldPropagateSuccessfulNullResult() {
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> output = new CompletableFuture<>();
    propagateResult(input, output);
    assertThat(output).isNotDone();

    input.complete(null);

    assertThat(output).isCompletedWithValue(null);
  }

  @Test
  public void shouldPropagateExceptionalResult() {
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> output = new CompletableFuture<>();
    propagateResult(input, output);
    assertThat(output).isNotDone();

    input.completeExceptionally(ERROR);

    assertCompletedExceptionally(output, ERROR);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldPropagateCancellation() {
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> output = mock(CompletableFuture.class);
    propagateCancellation(input, output);

    input.cancel(true);

    verify(output).cancel(true);
  }

  @Test
  public void shouldNotPropagateExceptionsOtherThanCancellationWhenPropagatingCancellation() {
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> output = new CompletableFuture<>();
    propagateCancellation(input, output);
    assertThat(output).isNotDone();

    input.completeExceptionally(ERROR);
    assertThat(output).isNotDone();
  }

  @Test
  public void shouldNotPropagateResultsWhenPropagatingCancellation() {
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> output = new CompletableFuture<>();
    propagateCancellation(input, output);
    assertThat(output).isNotDone();

    input.complete("foo");
    assertThat(output).isNotDone();
  }

  @Test
  public void shouldComposeExceptionallyWhenErrorOccurs() {
    final Function<Throwable, CompletionStage<String>> errorHandler = mockFunction();
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> afterException = new CompletableFuture<>();
    when(errorHandler.apply(ERROR)).thenReturn(afterException);

    final CompletableFuture<String> result = exceptionallyCompose(input, errorHandler);

    verifyNoInteractions(errorHandler);
    assertThat(result).isNotDone();

    // Completing input should trigger our error handler but not complete the result yet.
    input.completeExceptionally(ERROR);
    verify(errorHandler).apply(ERROR);
    assertThat(result).isNotDone();

    afterException.complete("Done");
    assertThat(result).isCompletedWithValue("Done");
  }

  @Test
  public void shouldComposeExceptionallyWhenErrorOccursAndComposedFutureFails() {
    final RuntimeException secondError = new RuntimeException("Again?");
    final Function<Throwable, CompletionStage<String>> errorHandler = mockFunction();
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> afterException = new CompletableFuture<>();
    when(errorHandler.apply(ERROR)).thenReturn(afterException);

    final CompletableFuture<String> result = exceptionallyCompose(input, errorHandler);

    verifyNoInteractions(errorHandler);
    assertThat(result).isNotDone();

    // Completing input should trigger our error handler but not complete the result yet.
    input.completeExceptionally(ERROR);
    verify(errorHandler).apply(ERROR);
    assertThat(result).isNotDone();

    afterException.completeExceptionally(secondError);
    assertCompletedExceptionally(result, secondError);
  }

  @Test
  public void shouldComposeExceptionallyWhenErrorOccursAndErrorHandlerThrowsException() {
    final Function<Throwable, CompletionStage<String>> errorHandler = mockFunction();
    final CompletableFuture<String> input = new CompletableFuture<>();
    final IllegalStateException thrownException = new IllegalStateException("Oops");
    when(errorHandler.apply(ERROR)).thenThrow(thrownException);

    final CompletableFuture<String> result = exceptionallyCompose(input, errorHandler);

    verifyNoInteractions(errorHandler);
    assertThat(result).isNotDone();

    // Completing input should trigger our error handler but not complete the result yet.
    input.completeExceptionally(ERROR);
    verify(errorHandler).apply(ERROR);

    assertCompletedExceptionally(result, thrownException);
  }

  @Test
  public void shouldNotCallErrorHandlerWhenFutureCompletesSuccessfully() {
    final Function<Throwable, CompletionStage<String>> errorHandler = mockFunction();
    final CompletableFuture<String> input = new CompletableFuture<>();
    final CompletableFuture<String> afterException = new CompletableFuture<>();
    when(errorHandler.apply(ERROR)).thenReturn(afterException);

    final CompletableFuture<String> result = exceptionallyCompose(input, errorHandler);

    verifyNoInteractions(errorHandler);
    assertThat(result).isNotDone();

    input.complete("Done");
    verifyNoInteractions(errorHandler);
    assertThat(result).isCompletedWithValue("Done");
  }

  private void assertCompletedExceptionally(
      final CompletableFuture<?> future, final RuntimeException expectedError) {
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .extracting(Throwable::getCause)
        .isSameAs(expectedError);
  }

  @SuppressWarnings("unchecked")
  private <I, O> Function<I, O> mockFunction() {
    return mock(Function.class);
  }
}
