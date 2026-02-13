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
package org.hyperledger.besu.evm.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class TracerAggregatorTest {

  @Test
  void shouldCreateEmptyAggregatorFromNoTracers() {
    final OperationTracer aggregator = TracerAggregator.of();

    assertThat(aggregator).isEqualTo(OperationTracer.NO_TRACING);
  }

  @Test
  void shouldReturnSingleTracerWhenOnlyOneProvided() {
    final OperationTracer mockTracer = mock(OperationTracer.class);

    final OperationTracer result = TracerAggregator.of(mockTracer);

    assertThat(result).isSameAs(mockTracer);
  }

  @Test
  void shouldCreateAggregatorFromMultipleTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);

    final OperationTracer result = TracerAggregator.of(tracer1, tracer2);

    assertThat(result).isInstanceOf(TracerAggregator.class);
    final TracerAggregator aggregator = (TracerAggregator) result;
    assertThat(aggregator.getTracers()).containsExactly(tracer1, tracer2);
  }

  @Test
  void shouldFilterOutNoTracingInstances() {
    final OperationTracer mockTracer = mock(OperationTracer.class);

    final OperationTracer result =
        TracerAggregator.of(OperationTracer.NO_TRACING, mockTracer, OperationTracer.NO_TRACING);

    assertThat(result).isSameAs(mockTracer);
  }

  @Test
  void shouldDelegateTracePreExecutionToAllTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final MessageFrame frame = mock(MessageFrame.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    aggregator.tracePreExecution(frame);

    verify(tracer1).tracePreExecution(frame);
    verify(tracer2).tracePreExecution(frame);
  }

  @Test
  void shouldDelegateTracePostExecutionToAllTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final MessageFrame frame = mock(MessageFrame.class);
    final OperationResult result = mock(OperationResult.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    aggregator.tracePostExecution(frame, result);

    verify(tracer1).tracePostExecution(frame, result);
    verify(tracer2).tracePostExecution(frame, result);
  }

  @Test
  void shouldDelegateTracePrecompileCallToAllTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final MessageFrame frame = mock(MessageFrame.class);
    final long gasRequirement = 100L;
    final Bytes output = Bytes.of(1, 2, 3);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    aggregator.tracePrecompileCall(frame, gasRequirement, output);

    verify(tracer1).tracePrecompileCall(frame, gasRequirement, output);
    verify(tracer2).tracePrecompileCall(frame, gasRequirement, output);
  }

  @Test
  void shouldDelegateTracePrepareTransactionToAllTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final WorldView worldView = mock(WorldView.class);
    final Transaction transaction = mock(Transaction.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    aggregator.tracePrepareTransaction(worldView, transaction);

    verify(tracer1).tracePrepareTransaction(worldView, transaction);
    verify(tracer2).tracePrepareTransaction(worldView, transaction);
  }

  @Test
  void shouldDelegateTraceStartTransactionToAllTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final WorldView worldView = mock(WorldView.class);
    final Transaction transaction = mock(Transaction.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    aggregator.traceStartTransaction(worldView, transaction);

    verify(tracer1).traceStartTransaction(worldView, transaction);
    verify(tracer2).traceStartTransaction(worldView, transaction);
  }

  @Test
  void shouldDelegateTraceEndTransactionToAllTracers() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final WorldView worldView = mock(WorldView.class);
    final Transaction transaction = mock(Transaction.class);
    final boolean status = true;
    final Bytes output = Bytes.of(1, 2, 3);
    final List<Log> logs = Collections.emptyList();
    final long gasUsed = 1000L;
    final Set<Address> selfDestructs = Set.of();
    final long timeNs = 123456L;

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    aggregator.traceEndTransaction(
        worldView, transaction, status, output, logs, gasUsed, selfDestructs, timeNs);

    verify(tracer1)
        .traceEndTransaction(
            worldView, transaction, status, output, logs, gasUsed, selfDestructs, timeNs);
    verify(tracer2)
        .traceEndTransaction(
            worldView, transaction, status, output, logs, gasUsed, selfDestructs, timeNs);
  }

  @Test
  void shouldReturnTrueForExtendedTracingIfAnyTracerReturnsTrue() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);

    when(tracer1.isExtendedTracing()).thenReturn(false);
    when(tracer2.isExtendedTracing()).thenReturn(true);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    assertThat(aggregator.isExtendedTracing()).isTrue();
  }

  @Test
  void shouldReturnFalseForExtendedTracingIfNoTracerReturnsTrue() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);

    when(tracer1.isExtendedTracing()).thenReturn(false);
    when(tracer2.isExtendedTracing()).thenReturn(false);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    assertThat(aggregator.isExtendedTracing()).isFalse();
  }

  @Test
  void shouldReturnTraceFramesFromFirstTracerThatProvidesNonEmptyFrames() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final OperationTracer tracer3 = mock(OperationTracer.class);

    final List<TraceFrame> expectedFrames = Arrays.asList(mock(TraceFrame.class));

    when(tracer1.getTraceFrames()).thenReturn(Collections.emptyList());
    when(tracer2.getTraceFrames()).thenReturn(expectedFrames);
    when(tracer3.getTraceFrames()).thenReturn(Collections.emptyList());

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2, tracer3);

    assertThat(aggregator.getTraceFrames()).isSameAs(expectedFrames);
  }

  @Test
  void shouldFindTracerByType() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final EthTransferLogOperationTracer tracer2 = mock(EthTransferLogOperationTracer.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    final Optional<EthTransferLogOperationTracer> found =
        aggregator.findTracer(EthTransferLogOperationTracer.class);

    assertThat(found).isPresent();
    assertThat(found.get()).isSameAs(tracer2);
  }

  @Test
  void shouldReturnEmptyWhenTracerNotFound() {
    final OperationTracer tracer1 = mock(OperationTracer.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1);

    final Optional<EthTransferLogOperationTracer> found =
        aggregator.findTracer(EthTransferLogOperationTracer.class);

    assertThat(found).isEmpty();
  }

  @Test
  void shouldFindTracerRecursivelyInNestedAggregators() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final EthTransferLogOperationTracer tracer2 = mock(EthTransferLogOperationTracer.class);
    final OperationTracer tracer3 = mock(OperationTracer.class);

    final TracerAggregator nestedAggregator = new TracerAggregator(tracer2, tracer3);
    final TracerAggregator mainAggregator = new TracerAggregator(tracer1, nestedAggregator);

    final Optional<EthTransferLogOperationTracer> found =
        mainAggregator.findTracer(EthTransferLogOperationTracer.class);

    assertThat(found).isPresent();
    assertThat(found.get()).isSameAs(tracer2);
  }

  @Test
  void shouldCheckHasTracerCorrectly() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final EthTransferLogOperationTracer tracer2 = mock(EthTransferLogOperationTracer.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    assertThat(aggregator.hasTracer(EthTransferLogOperationTracer.class)).isTrue();
    // Test for a tracer type that doesn't exist in this aggregator
    abstract class NonExistentTracer implements OperationTracer {}
    assertThat(aggregator.hasTracer(NonExistentTracer.class)).isFalse();
  }

  @Test
  void shouldCombineTracersCorrectlyWithNoTracing() {
    final EthTransferLogOperationTracer tracer = mock(EthTransferLogOperationTracer.class);

    final TracerAggregator result = TracerAggregator.combining(OperationTracer.NO_TRACING, tracer);

    assertThat(result.getTracers()).containsExactly(tracer);
  }

  @Test
  void shouldCombineTracersCorrectlyWithExistingTracer() {
    final OperationTracer baseTracer = mock(OperationTracer.class);
    final EthTransferLogOperationTracer additionalTracer =
        mock(EthTransferLogOperationTracer.class);

    final TracerAggregator result = TracerAggregator.combining(baseTracer, additionalTracer);

    assertThat(result.getTracers()).containsExactly(baseTracer, additionalTracer);
  }

  @Test
  void shouldMaintainTracerOrder() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final OperationTracer tracer2 = mock(OperationTracer.class);
    final OperationTracer tracer3 = mock(OperationTracer.class);

    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2, tracer3);
    final MessageFrame frame = mock(MessageFrame.class);

    aggregator.tracePreExecution(frame);

    // Verify that tracers are called in the correct order
    final var inOrder = org.mockito.Mockito.inOrder(tracer1, tracer2, tracer3);
    inOrder.verify(tracer1).tracePreExecution(frame);
    inOrder.verify(tracer2).tracePreExecution(frame);
    inOrder.verify(tracer3).tracePreExecution(frame);
  }

  @Test
  void staticHasTracerShouldCheckDirectTracer() {
    final EthTransferLogOperationTracer tracer = mock(EthTransferLogOperationTracer.class);

    assertThat(TracerAggregator.hasTracer(tracer, EthTransferLogOperationTracer.class)).isTrue();
    assertThat(TracerAggregator.hasTracer(tracer, OperationTracer.class)).isTrue();

    // Test for a tracer type that doesn't match
    abstract class NonExistentTracer implements OperationTracer {}
    assertThat(TracerAggregator.hasTracer(tracer, NonExistentTracer.class)).isFalse();
  }

  @Test
  void staticHasTracerShouldCheckAggregatedTracer() {
    final OperationTracer tracer1 = mock(OperationTracer.class);
    final EthTransferLogOperationTracer tracer2 = mock(EthTransferLogOperationTracer.class);
    final TracerAggregator aggregator = new TracerAggregator(tracer1, tracer2);

    assertThat(TracerAggregator.hasTracer(aggregator, EthTransferLogOperationTracer.class))
        .isTrue();
    assertThat(TracerAggregator.hasTracer(aggregator, OperationTracer.class)).isTrue();

    // Test for a tracer type that doesn't exist in the aggregator
    abstract class NonExistentTracer implements OperationTracer {}
    assertThat(TracerAggregator.hasTracer(aggregator, NonExistentTracer.class)).isFalse();
  }

  @Test
  void staticHasTracerShouldReturnFalseForNoTracing() {
    assertThat(
            TracerAggregator.hasTracer(
                OperationTracer.NO_TRACING, EthTransferLogOperationTracer.class))
        .isFalse();
  }
}
