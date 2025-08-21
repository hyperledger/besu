/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.OpCodeLoggerTracerResult;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.debug.TracerType;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("DebugTraceTransactionStepFactory")
class DebugTraceTransactionStepFactoryTest {

  private TransactionTrace mockTransactionTrace;
  private Transaction mockTransaction;
  private Hash mockHash;
  private TransactionProcessingResult mockResult;

  private static final String EXPECTED_HASH =
      "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

  @BeforeEach
  void setUp() {
    // Create mocks
    mockTransactionTrace = mock(TransactionTrace.class);
    mockTransaction = mock(Transaction.class);
    mockHash = mock(Hash.class);
    mockResult = mock(TransactionProcessingResult.class);

    // Set up transaction hash chain
    when(mockTransactionTrace.getTransaction()).thenReturn(mockTransaction);
    when(mockTransaction.getHash()).thenReturn(mockHash);
    when(mockHash.toHexString()).thenReturn(EXPECTED_HASH);

    // Minimal setup for DebugStructLoggerTracerResult - just enough to avoid NPE
    when(mockTransactionTrace.getGas()).thenReturn(0L);
    when(mockTransactionTrace.getResult()).thenReturn(mockResult);
    when(mockResult.getOutput()).thenReturn(Bytes.EMPTY);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockTransactionTrace.getTraceFrames()).thenReturn(Collections.emptyList());
  }

  @Test
  @DisplayName("should create function for OPCODE_TRACER that returns OpCodeLoggerTracerResult")
  void shouldCreateFunctionForOpcodeTracer() {
    // Given
    TracerType tracerType = TracerType.OPCODE_TRACER;
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);
    Function<TransactionTrace, DebugTraceTransactionResult> function =
        DebugTraceTransactionStepFactory.create(traceOptions);

    // When
    DebugTraceTransactionResult result = function.apply(mockTransactionTrace);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTxHash()).isEqualTo(EXPECTED_HASH);
    assertThat(result.getResult()).isInstanceOf(OpCodeLoggerTracerResult.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = TracerType.class,
      names = {"CALL_TRACER", "FLAT_CALL_TRACER"})
  @DisplayName("should create function for unimplemented tracers")
  void shouldCreateFunctionForNotYetImplementedTracers(final TracerType tracerType) {
    // Given
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);
    Function<TransactionTrace, DebugTraceTransactionResult> function =
        DebugTraceTransactionStepFactory.create(traceOptions);

    // When
    DebugTraceTransactionResult result = function.apply(mockTransactionTrace);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTxHash()).isEqualTo(EXPECTED_HASH);
    assertThat(result.getResult())
        .isInstanceOf(DebugTraceTransactionStepFactory.UnimplementedTracerResult.class);
  }

  @ParameterizedTest
  @EnumSource(TracerType.class)
  @DisplayName("should create non-null function for all tracer types")
  void shouldCreateNonNullFunctionForAllTracerTypes(final TracerType tracerType) {
    // When
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);
    Function<TransactionTrace, DebugTraceTransactionResult> function =
        DebugTraceTransactionStepFactory.create(traceOptions);

    // Then
    assertThat(function).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(TracerType.class)
  @DisplayName("should return non-null result with correct transaction hash for all tracer types")
  void shouldReturnNonNullResultWithCorrectTransactionHashForAllTracerTypes(
      final TracerType tracerType) {
    // Given
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);
    Function<TransactionTrace, DebugTraceTransactionResult> function =
        DebugTraceTransactionStepFactory.create(traceOptions);

    // When
    DebugTraceTransactionResult result = function.apply(mockTransactionTrace);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTxHash()).isEqualTo(EXPECTED_HASH);
    assertThat(result.getResult()).isNotNull();
  }

  @Test
  @DisplayName("should create async function for OPCODE_TRACER")
  void shouldCreateAsyncFunctionForOpcodeTracer() throws Exception {
    // Given
    Function<TransactionTrace, CompletableFuture<DebugTraceTransactionResult>> asyncFunction =
        DebugTraceTransactionStepFactory.createAsync(
            new TraceOptions(TracerType.OPCODE_TRACER, null, null));

    // When
    CompletableFuture<DebugTraceTransactionResult> future =
        asyncFunction.apply(mockTransactionTrace);
    DebugTraceTransactionResult result = future.get();

    // Then
    assertThat(future).isNotNull();
    assertThat(future.isDone()).isTrue();
    assertThat(result).isNotNull();
    assertThat(result.getTxHash()).isEqualTo(EXPECTED_HASH);
    assertThat(result.getResult()).isInstanceOf(OpCodeLoggerTracerResult.class);
  }

  @ParameterizedTest
  @EnumSource(TracerType.class)
  @DisplayName("should create non-null async function for all tracer types")
  void shouldCreateNonNullAsyncFunctionForAllTracerTypes(final TracerType tracerType) {
    // When
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);
    Function<TransactionTrace, CompletableFuture<DebugTraceTransactionResult>> asyncFunction =
        DebugTraceTransactionStepFactory.createAsync(traceOptions);

    // Then
    assertThat(asyncFunction).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(TracerType.class)
  @DisplayName("should return completed future with non-null result for all tracer types")
  void shouldReturnCompletedFutureWithNonNullResultForAllTracerTypes(final TracerType tracerType)
      throws Exception {
    // Given
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);
    Function<TransactionTrace, CompletableFuture<DebugTraceTransactionResult>> asyncFunction =
        DebugTraceTransactionStepFactory.createAsync(traceOptions);

    // When
    CompletableFuture<DebugTraceTransactionResult> future =
        asyncFunction.apply(mockTransactionTrace);
    DebugTraceTransactionResult result = future.get();

    // Then
    assertThat(future).isNotNull();
    assertThat(future.isDone()).isTrue();
    assertThat(result).isNotNull();
    assertThat(result.getTxHash()).isEqualTo(EXPECTED_HASH);
    assertThat(result.getResult()).isNotNull();
  }

  @Test
  @DisplayName("should produce same result type as synchronous version")
  void shouldProduceSameResultTypeAsSynchronousVersion() throws Exception {
    // Given
    TracerType tracerType = TracerType.OPCODE_TRACER;
    TraceOptions traceOptions = new TraceOptions(tracerType, null, null);

    Function<TransactionTrace, DebugTraceTransactionResult> syncFunction =
        DebugTraceTransactionStepFactory.create(traceOptions);
    Function<TransactionTrace, CompletableFuture<DebugTraceTransactionResult>> asyncFunction =
        DebugTraceTransactionStepFactory.createAsync(traceOptions);

    // When
    DebugTraceTransactionResult syncResult = syncFunction.apply(mockTransactionTrace);
    DebugTraceTransactionResult asyncResult = asyncFunction.apply(mockTransactionTrace).get();

    // Then
    assertThat(asyncResult.getTxHash()).isEqualTo(syncResult.getTxHash());
    assertThat(asyncResult.getResult().getClass()).isEqualTo(syncResult.getResult().getClass());
  }
}
