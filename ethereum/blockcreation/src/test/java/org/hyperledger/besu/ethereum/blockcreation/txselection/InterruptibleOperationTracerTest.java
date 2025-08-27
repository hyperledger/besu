/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.blockcreation.txselection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InterruptibleOperationTracerTest {

  @Mock private BlockAwareOperationTracer delegateTracer;
  @Mock private MessageFrame messageFrame;
  @Mock private Operation.OperationResult operationResult;
  @Mock private WorldView worldView;
  @Mock private Transaction transaction;
  @Mock private BlockHeader blockHeader;
  @Mock private BlockBody blockBody;

  private InterruptibleOperationTracer interruptibleTracer;

  @BeforeEach
  public void setUp() {
    interruptibleTracer = new InterruptibleOperationTracer(delegateTracer);
  }

  @Test
  public void shouldDelegateAllMethodsWhenNotInterrupted() {
    interruptibleTracer.tracePreExecution(messageFrame);
    verify(delegateTracer, times(1)).tracePreExecution(messageFrame);

    interruptibleTracer.tracePostExecution(messageFrame, operationResult);
    verify(delegateTracer, times(1)).tracePostExecution(messageFrame, operationResult);

    interruptibleTracer.tracePrecompileCall(messageFrame, 1000L, Bytes.EMPTY);
    verify(delegateTracer, times(1)).tracePrecompileCall(messageFrame, 1000L, Bytes.EMPTY);

    interruptibleTracer.traceContextEnter(messageFrame);
    verify(delegateTracer, times(1)).traceContextEnter(messageFrame);

    interruptibleTracer.traceContextReEnter(messageFrame);
    verify(delegateTracer, times(1)).traceContextReEnter(messageFrame);

    interruptibleTracer.traceContextExit(messageFrame);
    verify(delegateTracer, times(1)).traceContextExit(messageFrame);

    interruptibleTracer.traceAccountCreationResult(messageFrame, Optional.empty());
    verify(delegateTracer, times(1)).traceAccountCreationResult(messageFrame, Optional.empty());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTracePreExecution() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> interruptibleTracer.tracePreExecution(messageFrame))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).tracePreExecution(any());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTracePostExecution() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> interruptibleTracer.tracePostExecution(messageFrame, operationResult))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).tracePostExecution(any(), any());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTracePrecompileCall() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(
            () -> interruptibleTracer.tracePrecompileCall(messageFrame, 1000L, Bytes.EMPTY))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).tracePrecompileCall(any(), any(Long.class), any());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTraceContextEnter() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> interruptibleTracer.traceContextEnter(messageFrame))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).traceContextEnter(any());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTraceContextReEnter() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> interruptibleTracer.traceContextReEnter(messageFrame))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).traceContextReEnter(any());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTraceContextExit() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> interruptibleTracer.traceContextExit(messageFrame))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).traceContextExit(any());
  }

  @Test
  public void shouldThrowExceptionWhenThreadIsInterruptedDuringTraceAccountCreationResult() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(
            () -> interruptibleTracer.traceAccountCreationResult(messageFrame, Optional.empty()))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class)
        .hasMessageContaining("Transaction execution interrupted");

    verify(delegateTracer, never()).traceAccountCreationResult(any(), any());
  }

  @Test
  public void shouldNotCheckInterruptionForNonTracingMethods() {
    interruptibleTracer.tracePrepareTransaction(worldView, transaction);
    verify(delegateTracer, times(1)).tracePrepareTransaction(worldView, transaction);

    interruptibleTracer.traceStartTransaction(worldView, transaction);
    verify(delegateTracer, times(1)).traceStartTransaction(worldView, transaction);

    interruptibleTracer.traceBeforeRewardTransaction(worldView, transaction, Wei.ZERO);
    verify(delegateTracer, times(1))
        .traceBeforeRewardTransaction(worldView, transaction, Wei.ZERO);

    interruptibleTracer.traceEndTransaction(
        worldView, transaction, true, Bytes.EMPTY, List.of(), 0L, Set.of(), 0L);
    verify(delegateTracer, times(1))
        .traceEndTransaction(
            worldView, transaction, true, Bytes.EMPTY, List.of(), 0L, Set.of(), 0L);
  }

  @Test
  public void shouldDelegateIsExtendedTracing() {
    when(delegateTracer.isExtendedTracing()).thenReturn(true);
    assertThat(interruptibleTracer.isExtendedTracing()).isTrue();

    when(delegateTracer.isExtendedTracing()).thenReturn(false);
    assertThat(interruptibleTracer.isExtendedTracing()).isFalse();
  }

  @Test
  public void shouldHandleConcurrentInterruption() throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(1);
    AtomicBoolean exceptionThrown = new AtomicBoolean(false);
    AtomicInteger executionCount = new AtomicInteger(0);

    executor.submit(
        () -> {
          try {
            startLatch.await();
            for (int i = 0; i < 100; i++) {
              interruptibleTracer.tracePreExecution(messageFrame);
              executionCount.incrementAndGet();
              Thread.sleep(1);
            }
          } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
              exceptionThrown.set(true);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            endLatch.countDown();
          }
        });

    executor.submit(
        () -> {
          try {
            startLatch.countDown();
            Thread.sleep(50);
            executor.shutdownNow();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    endLatch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(exceptionThrown.get()).isTrue();
    assertThat(executionCount.get()).isLessThan(100);
  }

  @Test
  public void shouldClearInterruptFlagAfterException() {
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> interruptibleTracer.tracePreExecution(messageFrame))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(InterruptedException.class);

    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

  @Test
  public void shouldProperlyDelegateBlockTracingMethods() {
    Address miningBeneficiary = Address.fromHexString("0x1");

    interruptibleTracer.traceStartBlock(worldView, blockHeader, blockBody, miningBeneficiary);
    verify(delegateTracer, times(1))
        .traceStartBlock(worldView, blockHeader, blockBody, miningBeneficiary);

    interruptibleTracer.traceEndBlock(blockHeader, blockBody);
    verify(delegateTracer, times(1)).traceEndBlock(blockHeader, blockBody);
  }
}