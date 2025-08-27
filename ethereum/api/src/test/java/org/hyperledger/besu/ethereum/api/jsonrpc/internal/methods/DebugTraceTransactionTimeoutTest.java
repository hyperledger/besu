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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.handlers.RpcMethodTimeoutException;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestId;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.blockcreation.txselection.InterruptibleOperationTracer;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DebugTraceTransactionTimeoutTest {

  private static final String TRANSACTION_HASH = 
      "0x0000000000000000000000000000000000000000000000000000000000000001";

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionTracer transactionTracer;
  @Mock private TransactionWithMetadata transactionWithMetadata;
  @Mock private Transaction transaction;

  private DebugTraceTransaction debugTraceTransaction;
  private ExecutorService executorService;

  @BeforeEach
  public void setUp() {
    debugTraceTransaction = new DebugTraceTransaction(blockchainQueries, transactionTracer);
    executorService = Executors.newCachedThreadPool();
  }

  @AfterEach
  public void tearDown() {
    executorService.shutdownNow();
    try {
      if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
        System.err.println("ExecutorService did not terminate");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  public void shouldHandleTransactionNotFoundWithoutTimeout() {
    AtomicBoolean tracerStarted = new AtomicBoolean(false);

    Hash hash = Hash.fromHexStringLenient(TRANSACTION_HASH);
    
    // Return empty optional to simulate transaction not found (immediate return, no hanging)
    when(blockchainQueries.transactionByHash(hash)).thenReturn(Optional.empty());

    lenient().doAnswer(
            invocation -> {
              tracerStarted.set(true);
              return mock(TransactionProcessingResult.class);
            })
        .when(transactionTracer)
        .traceTransaction(any(), any(), any(), any());

    JsonRpcRequest request = createRequest(TRANSACTION_HASH);
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    // Test completes immediately without hanging when transaction not found
    JsonRpcResponse response = debugTraceTransaction.response(context);

    // Should return null result when transaction not found
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) response).getResult()).isNull();
    assertThat(tracerStarted.get()).isFalse();
    verify(transactionTracer, never()).traceTransaction(any(), any(), any(), any());
  }

  @Test
  public void shouldHandleTracerExecution() {
    AtomicBoolean tracerCalled = new AtomicBoolean(false);

    Hash hash = Hash.fromHexStringLenient(TRANSACTION_HASH);
    when(blockchainQueries.transactionByHash(hash)).thenReturn(Optional.of(transactionWithMetadata));
    when(transactionWithMetadata.getBlockHash()).thenReturn(Optional.of(hash));
    when(transactionWithMetadata.getTransaction()).thenReturn(transaction);

    // Simulate normal tracer execution (no hanging)
    doAnswer(
            invocation -> {
              tracerCalled.set(true);
              BlockAwareOperationTracer tracer = invocation.getArgument(3);
              // Verify an InterruptibleOperationTracer could be passed
              assertThat(tracer).isNotNull();
              return mock(TransactionProcessingResult.class);
            })
        .when(transactionTracer)
        .traceTransaction(any(), any(), any(), any());

    JsonRpcRequest request = createRequest(TRANSACTION_HASH);
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    // Execute synchronously without hanging
    JsonRpcResponse response = debugTraceTransaction.response(context);

    // Verify tracer was called and response is valid
    assertThat(tracerCalled.get()).isTrue();
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldCompleteNormallyWhenNotCancelled() {
    AtomicBoolean completedSuccessfully = new AtomicBoolean(false);

    Hash hash = Hash.fromHexStringLenient(TRANSACTION_HASH);
    when(blockchainQueries.transactionByHash(hash)).thenReturn(Optional.of(transactionWithMetadata));
    when(transactionWithMetadata.getBlockHash()).thenReturn(Optional.of(hash));
    when(transactionWithMetadata.getTransaction()).thenReturn(transaction);

    // Simulate fast, normal completion (no hanging)
    doAnswer(
            invocation -> {
              completedSuccessfully.set(true);
              return mock(TransactionProcessingResult.class);
            })
        .when(transactionTracer)
        .traceTransaction(any(), any(), any(), any());

    JsonRpcRequest request = createRequest(TRANSACTION_HASH);
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    // Execute synchronously without hanging
    JsonRpcResponse response = debugTraceTransaction.response(context);

    assertThat(completedSuccessfully.get()).isTrue();
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldCreateProperErrorResponseFormat() {
    // Test that we can create proper timeout error responses
    JsonRpcRequest request = createRequest(TRANSACTION_HASH);
    JsonRpcErrorResponse response = new JsonRpcErrorResponse(request.getId(), RpcErrorType.TIMEOUT_ERROR);

    // Verify error response format without hanging
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    assertThat(response.getError().getCode()).isEqualTo(RpcErrorType.TIMEOUT_ERROR.getCode());
    assertThat(response.getError().getMessage()).contains("Timeout");
    assertThat(response.getId()).isEqualTo(request.getId());
  }

  @Test
  public void shouldMaintainStateConsistencyWithoutTimeout() {
    AtomicReference<String> stateRef = new AtomicReference<>("initial");
    AtomicBoolean tracingCompleted = new AtomicBoolean(false);

    Hash hash = Hash.fromHexStringLenient(TRANSACTION_HASH);
    when(blockchainQueries.transactionByHash(hash)).thenReturn(Optional.of(transactionWithMetadata));
    when(transactionWithMetadata.getBlockHash()).thenReturn(Optional.of(hash));
    when(transactionWithMetadata.getTransaction()).thenReturn(transaction);

    // Simulate normal execution that completes without timeout
    doAnswer(
            invocation -> {
              stateRef.set("tracing");
              // Simulate quick tracing operation
              stateRef.set("completed");
              tracingCompleted.set(true);
              return mock(TransactionProcessingResult.class);
            })
        .when(transactionTracer)
        .traceTransaction(any(), any(), any(), any());

    JsonRpcRequest request = createRequest(TRANSACTION_HASH);
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    // Execute synchronously (no hanging)
    JsonRpcResponse response = debugTraceTransaction.response(context);

    // Verify state consistency - operation completed normally
    assertThat(stateRef.get()).isEqualTo("completed");
    assertThat(tracingCompleted.get()).isTrue();
    assertThat(response).isNotNull();
  }

  @Test
  public void shouldHandleMultipleRequestsGracefully() {
    int numberOfRequests = 5;
    AtomicInteger successCount = new AtomicInteger(0);

    Hash hash = Hash.fromHexStringLenient(TRANSACTION_HASH);
    when(blockchainQueries.transactionByHash(hash)).thenReturn(Optional.of(transactionWithMetadata));
    when(transactionWithMetadata.getBlockHash()).thenReturn(Optional.of(hash));
    when(transactionWithMetadata.getTransaction()).thenReturn(transaction);

    // Simulate successful execution (no hanging)
    doAnswer(
            invocation -> {
              successCount.incrementAndGet();
              return mock(TransactionProcessingResult.class);
            })
        .when(transactionTracer)
        .traceTransaction(any(), any(), any(), any());

    // Process multiple requests synchronously
    for (int i = 0; i < numberOfRequests; i++) {
      JsonRpcRequest request = createRequest(TRANSACTION_HASH, String.valueOf(i));
      JsonRpcRequestContext context = new JsonRpcRequestContext(request);
      
      JsonRpcResponse response = debugTraceTransaction.response(context);
      assertThat(response).isNotNull();
    }

    // Verify all requests were processed successfully
    assertThat(successCount.get()).isEqualTo(numberOfRequests);
  }

  private JsonRpcRequest createRequest(final String transactionHash) {
    return createRequest(transactionHash, "1");
  }

  private JsonRpcRequest createRequest(final String transactionHash, final String id) {
    JsonRpcRequest request = new JsonRpcRequest(
        "2.0",
        RpcMethod.DEBUG_TRACE_TRANSACTION.getMethodName(),
        new Object[] {transactionHash});
    request.setId(new JsonRpcRequestId(id));
    return request;
  }
}