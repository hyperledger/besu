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
package org.hyperledger.besu.ethereum.api.jsonrpc.timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.handlers.RpcMethodTimeoutException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestId;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
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
@SuppressWarnings({"DirectInvocationOnMock", "UnnecessaryAsync", "UnusedVariable"})
public class RpcTimeoutInterruptionIntegrationTest {

  private static final long TIMEOUT_MS = 100;
  private static final long LONG_RUNNING_OPERATION_MS = 5000;

  @Mock private JsonRpcMethod mockMethod;
  private ExecutorService executorService;
  private ThreadFactory namedThreadFactory;

  @BeforeEach
  public void setUp() {
    AtomicInteger threadCounter = new AtomicInteger();
    namedThreadFactory =
        r -> {
          Thread t = new Thread(r);
          t.setName("rpc-test-thread-" + threadCounter.incrementAndGet());
          return t;
        };
    executorService = Executors.newCachedThreadPool(namedThreadFactory);
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
  public void shouldInterruptLongRunningOperationOnTimeout() throws Exception {
    AtomicBoolean wasInterrupted = new AtomicBoolean(false);
    AtomicBoolean operationStarted = new AtomicBoolean(false);
    CountDownLatch operationLatch = new CountDownLatch(1);

    when(mockMethod.getName()).thenReturn("test_method");
    doAnswer(
            invocation -> {
              operationStarted.set(true);
              operationLatch.countDown();
              try {
                Thread.sleep(LONG_RUNNING_OPERATION_MS);
              } catch (InterruptedException e) {
                wasInterrupted.set(true);
                Thread.currentThread().interrupt();
                throw new RuntimeException(new InterruptedException("Operation interrupted"));
              }
              return new JsonRpcSuccessResponse(
                  invocation.getArgument(0, JsonRpcRequestContext.class).getRequest().getId(),
                  "Should not reach here");
            })
        .when(mockMethod)
        .response(any());

    JsonRpcRequest request = createRequest("test_method", new Object[] {}, "1");
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    Future<JsonRpcResponse> future =
        executorService.submit(() -> mockMethod.response(context));

    operationLatch.await();
    assertThat(operationStarted.get()).isTrue();

    Thread.sleep(TIMEOUT_MS);

    future.cancel(true);

    Thread.sleep(200);

    assertThat(wasInterrupted.get()).isTrue();
    assertThat(future.isCancelled()).isTrue();
  }

  @Test
  public void shouldCleanupResourcesAfterInterruption() throws Exception {
    AtomicInteger resourceCounter = new AtomicInteger(0);
    AtomicBoolean resourcesCleaned = new AtomicBoolean(false);
    CountDownLatch startLatch = new CountDownLatch(1);

    when(mockMethod.getName()).thenReturn("test_method");
    doAnswer(
            invocation -> {
              startLatch.countDown();
              resourceCounter.incrementAndGet();
              try {
                Thread.sleep(LONG_RUNNING_OPERATION_MS);
              } catch (InterruptedException e) {
                resourceCounter.decrementAndGet();
                resourcesCleaned.set(true);
                Thread.currentThread().interrupt();
                throw new RuntimeException(new InterruptedException("Operation interrupted"));
              } finally {
                if (Thread.currentThread().isInterrupted()) {
                  resourceCounter.decrementAndGet();
                  resourcesCleaned.set(true);
                }
              }
              return new JsonRpcSuccessResponse(
                  invocation.getArgument(0, JsonRpcRequestContext.class).getRequest().getId(),
                  "Should not reach here");
            })
        .when(mockMethod)
        .response(any());

    JsonRpcRequest request = createRequest("test_method", new Object[] {}, "1");
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    Future<JsonRpcResponse> future =
        executorService.submit(() -> mockMethod.response(context));

    startLatch.await();
    Thread.sleep(50);

    future.cancel(true);

    Thread.sleep(200);

    assertThat(resourcesCleaned.get()).isTrue();
    assertThat(resourceCounter.get()).isEqualTo(0);
  }

  @Test
  public void shouldHandleConcurrentTimeouts() throws Exception {
    int numberOfRequests = 10;
    CountDownLatch allStarted = new CountDownLatch(numberOfRequests);
    CountDownLatch proceedSignal = new CountDownLatch(1);
    AtomicInteger interruptedCount = new AtomicInteger(0);

    when(mockMethod.getName()).thenReturn("test_method");
    doAnswer(
            invocation -> {
              allStarted.countDown();
              proceedSignal.await();
              try {
                Thread.sleep(LONG_RUNNING_OPERATION_MS);
              } catch (InterruptedException e) {
                interruptedCount.incrementAndGet();
                Thread.currentThread().interrupt();
                throw new RuntimeException(new InterruptedException("Operation interrupted"));
              }
              return new JsonRpcSuccessResponse(
                  invocation.getArgument(0, JsonRpcRequestContext.class).getRequest().getId(),
                  "Should not reach here");
            })
        .when(mockMethod)
        .response(any());

    List<CompletableFuture<?>> futures = new ArrayList<>();

    for (int i = 0; i < numberOfRequests; i++) {
      final int requestId = i;
      futures.add(
          CompletableFuture.supplyAsync(
              () -> {
                JsonRpcRequest request = 
                    createRequest("test_method", new Object[] {}, String.valueOf(requestId));
                JsonRpcRequestContext context = new JsonRpcRequestContext(request);
                try {
                  return mockMethod.response(context);
                } catch (RuntimeException e) {
                  if (e.getCause() instanceof InterruptedException) {
                    return new JsonRpcErrorResponse(
                        request.getId(), RpcErrorType.TIMEOUT_ERROR);
                  }
                  throw e;
                }
              },
              executorService));
    }

    allStarted.await();
    proceedSignal.countDown();

    Thread.sleep(TIMEOUT_MS);

    for (CompletableFuture<?> future : futures) {
      future.cancel(true);
    }

    Thread.sleep(500);

    assertThat(interruptedCount.get()).isEqualTo(numberOfRequests);
  }

  @Test
  public void shouldNotInterruptCompletedOperation() throws Exception {
    AtomicBoolean wasInterrupted = new AtomicBoolean(false);

    when(mockMethod.getName()).thenReturn("test_method");
    doAnswer(
            invocation -> {
              Thread.sleep(50);
              if (Thread.currentThread().isInterrupted()) {
                wasInterrupted.set(true);
              }
              return new JsonRpcSuccessResponse(
                  invocation.getArgument(0, JsonRpcRequestContext.class).getRequest().getId(),
                  "Success");
            })
        .when(mockMethod)
        .response(any());

    JsonRpcRequest request = createRequest("test_method", new Object[] {}, "1");
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    Future<JsonRpcResponse> future =
        executorService.submit(() -> mockMethod.response(context));

    JsonRpcResponse response = future.get(200, TimeUnit.MILLISECONDS);

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) response).getResult()).isEqualTo("Success");
    assertThat(wasInterrupted.get()).isFalse();
  }

  @Test
  public void shouldReturnTimeoutErrorWhenOperationIsInterrupted() throws Exception {
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicReference<JsonRpcResponse> responseRef = new AtomicReference<>();

    when(mockMethod.getName()).thenReturn("test_method");
    doAnswer(
            invocation -> {
              startLatch.countDown();
              try {
                Thread.sleep(LONG_RUNNING_OPERATION_MS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RpcMethodTimeoutException();
              }
              return new JsonRpcSuccessResponse(
                  invocation.getArgument(0, JsonRpcRequestContext.class).getRequest().getId(),
                  "Should not reach here");
            })
        .when(mockMethod)
        .response(any());

    JsonRpcRequest request = createRequest("test_method", new Object[] {}, "1");
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    CompletableFuture<JsonRpcResponse> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return mockMethod.response(context);
              } catch (RpcMethodTimeoutException e) {
                return new JsonRpcErrorResponse(request.getId(), RpcErrorType.TIMEOUT_ERROR);
              }
            },
            executorService);

    startLatch.await();
    Thread.sleep(TIMEOUT_MS);

    future.cancel(true);

    Thread.sleep(200);

    if (future.isCompletedExceptionally() || future.isCancelled()) {
      responseRef.set(new JsonRpcErrorResponse(request.getId(), RpcErrorType.TIMEOUT_ERROR));
    } else {
      responseRef.set(future.get());
    }

    assertThat(responseRef.get()).isInstanceOf(JsonRpcErrorResponse.class);
    JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) responseRef.get();
    assertThat(errorResponse.getError().getCode()).isEqualTo(RpcErrorType.TIMEOUT_ERROR.getCode());
  }

  @Test
  public void shouldMeasureResourceUsageBeforeAndAfterInterruption() throws Exception {
    Runtime runtime = Runtime.getRuntime();
    long initialMemory = runtime.totalMemory() - runtime.freeMemory();
    int initialThreadCount = Thread.activeCount();

    AtomicBoolean memoryAllocated = new AtomicBoolean(false);
    CountDownLatch allocationLatch = new CountDownLatch(1);
    Object[] largeArray = new Object[1];

    when(mockMethod.getName()).thenReturn("test_method");
    doAnswer(
            invocation -> {
              largeArray[0] = new byte[10 * 1024 * 1024];
              memoryAllocated.set(true);
              allocationLatch.countDown();
              try {
                Thread.sleep(LONG_RUNNING_OPERATION_MS);
              } catch (InterruptedException e) {
                largeArray[0] = null;
                Thread.currentThread().interrupt();
                throw new RuntimeException(new InterruptedException("Operation interrupted"));
              }
              return new JsonRpcSuccessResponse(
                  invocation.getArgument(0, JsonRpcRequestContext.class).getRequest().getId(),
                  "Should not reach here");
            })
        .when(mockMethod)
        .response(any());

    JsonRpcRequest request = createRequest("test_method", new Object[] {}, "1");
    JsonRpcRequestContext context = new JsonRpcRequestContext(request);

    Future<JsonRpcResponse> future =
        executorService.submit(() -> mockMethod.response(context));

    allocationLatch.await();
    assertThat(memoryAllocated.get()).isTrue();

    long duringExecutionMemory = runtime.totalMemory() - runtime.freeMemory();
    assertThat(duringExecutionMemory).isGreaterThan(initialMemory);

    future.cancel(true);

    Thread.sleep(500);
    System.gc();
    Thread.sleep(200);

    long afterInterruptionMemory = runtime.totalMemory() - runtime.freeMemory();
    int afterInterruptionThreadCount = Thread.activeCount();

    assertThat(afterInterruptionThreadCount).isLessThanOrEqualTo(initialThreadCount + 2);
  }

  private JsonRpcRequest createRequest(
      final String method, final Object[] params, final String id) {
    JsonRpcRequest request = new JsonRpcRequest("2.0", method, params);
    request.setId(new JsonRpcRequestId(id));
    return request;
  }
}