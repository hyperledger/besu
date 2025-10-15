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
package org.hyperledger.besu.ethereum.api.jsonrpc.timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.AbstractJsonRpcHttpServiceTest;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.debug.OpCodeTracerConfig;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.tracing.CancellableOperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RpcTimeoutInterruptionIntegrationTest extends AbstractJsonRpcHttpServiceTest {

  private static final long HTTP_TIMEOUT_MS = 400;
  private final AtomicReference<SlowDebugOperationTracer> slowTracerRef = new AtomicReference<>();
  private final AtomicReference<CancellableOperationTracer> cancellableTracerSpy =
      new AtomicReference<>();
  private final AtomicReference<RuntimeException> caughtInterruptException =
      new AtomicReference<>();
  private volatile long tracerDelayMs = HTTP_TIMEOUT_MS; // Default to slow delay
  private Hash toTrace;

  @BeforeEach
  public void confirmSetup() {
    super.startService();
    // Get a transaction hash from the blockchain
    final Block block = blockchainSetupUtil.getBlockchain().getChainHeadBlock();
    assertThat(block).as("Chain head block should exist").isNotNull();
    assertThat(block.getHeader().getNumber())
        .as("Block should have a valid number")
        .isGreaterThan(0L);
    assertThat(block.getBody().getTransactions())
        .as("Block should contain transactions for tracing test")
        .isNotEmpty();

    final Hash txHash = block.getBody().getTransactions().get(0).getHash();
    assertThat(txHash).as("Transaction hash should exist").isNotNull();
    this.toTrace = txHash;
  }

  @Override
  protected Map<String, JsonRpcMethod> getRpcMethods(
      final JsonRpcConfiguration config, final BlockchainSetupUtil blockchainSetupUtil) {
    final Map<String, JsonRpcMethod> methods = super.getRpcMethods(config, blockchainSetupUtil);

    // Get the blockchain queries and transaction tracer from the setup
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            blockchainSetupUtil.getProtocolSchedule(),
            blockchainSetupUtil.getBlockchain(),
            blockchainSetupUtil.getWorldArchive(),
            null);

    final TransactionTracer transactionTracer =
        new TransactionTracer(
            new BlockReplay(
                blockchainSetupUtil.getProtocolSchedule(),
                blockchainSetupUtil.getProtocolContext(),
                blockchainSetupUtil.getBlockchain()));

    // Replace debug_traceTransaction with one that uses a slow tracer and spy
    methods.put(
        "debug_traceTransaction",
        new DebugTraceTransaction(
            blockchainQueries,
            transactionTracer,
            options -> {
              SlowDebugOperationTracer delegate =
                  new SlowDebugOperationTracer(options.opCodeTracerConfig(), true, tracerDelayMs);
              slowTracerRef.set(delegate);

              // Create a custom wrapper that captures exceptions
              CancellableOperationTracer exceptionCapturingTracer =
                  new CancellableOperationTracer(delegate) {
                    @Override
                    public void tracePreExecution(final MessageFrame frame) {
                      try {
                        super.tracePreExecution(frame);
                      } catch (RuntimeException e) {
                        caughtInterruptException.set(e);
                        throw e; // Re-throw to maintain normal flow
                      }
                    }
                  };

              // Spy on the exception-capturing tracer
              CancellableOperationTracer spiedTracer = spy(exceptionCapturingTracer);
              doCallRealMethod().when(spiedTracer).tracePreExecution(any());
              doCallRealMethod()
                  .when(spiedTracer)
                  .traceEndTransaction(
                      any(), any(), anyBoolean(), any(), any(), anyLong(), any(), anyLong());
              cancellableTracerSpy.set(spiedTracer);
              return spiedTracer;
            }));

    return methods;
  }

  /**
   * A DebugOperationTracer that adds artificial delays to simulate slow execution. This ensures the
   * test reliably demonstrates the timeout issue regardless of machine speed.
   */
  private static class SlowDebugOperationTracer extends DebugOperationTracer {
    private final long delayMs;
    private int opCounter = 0;
    private int txCounter = 0;

    public SlowDebugOperationTracer(
        final OpCodeTracerConfig opCodeTracerConfig,
        final boolean recordMemory,
        final long delayMs) {
      super(opCodeTracerConfig, recordMemory);
      this.delayMs = delayMs;
    }

    @Override
    public void tracePreExecution(final MessageFrame frame) {
      opCounter++;
      super.tracePreExecution(frame);
      try {
        // Sleep for the configured delay
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore interrupt status
        // Let the interrupt bubble up - CancellableOperationTracer will handle it
      }
    }

    @Override
    public void traceEndTransaction(
        final WorldView worldView,
        final Transaction tx,
        final boolean status,
        final Bytes output,
        final List<Log> logs,
        final long gasUsed,
        final Set<Address> selfDestructs,
        final long timeNs) {
      txCounter++;
      super.traceEndTransaction(
          worldView, tx, status, output, logs, gasUsed, selfDestructs, timeNs);
      try {
        // Sleep for the configured delay
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore interrupt status
        // Let the interrupt bubble up - CancellableOperationTracer will handle it
      }
    }

    public int getOperationsCount() {
      return opCounter;
    }

    public int getTxCounter() {
      return txCounter;
    }
  }

  @Test
  public void shouldInterruptWhenClientTimesOutBeforeServer() throws Exception {
    // Client should timeout first, triggering connection close handler which interrupts worker
    // thread
    // Fix IPv4/IPv6 localhost resolution issue - ensure we use 127.0.0.1 instead of localhost
    // TODO why did we need to do this during local testing on MacOS
    final String fixedBaseUrl = baseUrl.replace("localhost", "127.0.0.1");

    // Create debug trace request
    final String requestJson =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"debug_traceTransaction\",\"params\":[\"%s\"],\"id\":1}",
            this.toTrace.toHexString());

    final RequestBody requestBody = RequestBody.create(requestJson, JSON);
    final Request request = new Request.Builder().url(fixedBaseUrl).post(requestBody).build();

    // Set up a client with configurable timeout
    final okhttp3.OkHttpClient timeoutClient =
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

    // Make synchronous request that should timeout
    try {
      timeoutClient.newCall(request).execute();
    } catch (IOException e) {
      // Expected: Server closes connection due to timeout - can be SocketTimeoutException or
      // IOException
      // Server-side timeout causes "unexpected end of stream" when connection is terminated
      boolean isExpectedTimeoutError =
          e instanceof SocketTimeoutException
              || e.getMessage().contains("unexpected end of stream")
              || e.getMessage().contains("stream was reset")
              || e.getMessage().contains("connection was closed");

      assertThat(isExpectedTimeoutError)
          .as(
              "HTTP call should fail with timeout-related error, but got: "
                  + e.getClass().getSimpleName()
                  + ": "
                  + e.getMessage())
          .isTrue();
    }

    SlowDebugOperationTracer sdot = slowTracerRef.get();
    CancellableOperationTracer spiedTracer = cancellableTracerSpy.get();

    // First verify that both tracers were created
    assertThat(sdot)
        .as("SlowDebugOperationTracer should have been created by the tracer factory")
        .isNotNull();

    // Verify the tracer made some progress before being interrupted
    assertThat(sdot.getOperationsCount())
        .as("Tracer should have processed at least one operation before interruption")
        .isGreaterThan(0);

    // The key test: Verify that tracePreExecution was called at least once
    // The HTTP timeout should have interrupted the thread during the slow trace operation
    verify(spiedTracer).tracePreExecution(any(MessageFrame.class));

    // Most importantly: Verify that the CancellableOperationTracer threw the expected
    // RuntimeException
    // when it detected the thread interrupt
    RuntimeException capturedException = caughtInterruptException.get();
    assertThat(capturedException)
        .as(
            "CancellableOperationTracer should have thrown RuntimeException when thread was interrupted")
        .isNotNull()
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Trace execution interrupted");

    // Verify it has the correct cause
    assertThat(capturedException.getCause())
        .as("RuntimeException should be caused by InterruptedException")
        .isInstanceOf(InterruptedException.class)
        .hasMessage("Trace execution interrupted");
  }

  @Test
  public void shouldCompleteSuccessfullyWhenNoInterruptOccurs() throws Exception {
    // Set up a scenario where the operation completes before any timeout
    // Configure a short delay (50ms) so the operation completes quickly
    tracerDelayMs = 0; // Much shorter than any timeout

    final String fixedBaseUrl = baseUrl.replace("localhost", "127.0.0.1");

    // Create debug trace request
    final String requestJson =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"debug_traceTransaction\",\"params\":[\"%s\"],\"id\":1}",
            this.toTrace.toHexString());

    final RequestBody requestBody = RequestBody.create(requestJson, JSON);
    final Request request = new Request.Builder().url(fixedBaseUrl).post(requestBody).build();

    // Set up a client with a longer timeout than the operation duration
    final okhttp3.OkHttpClient longTimeoutClient =
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_MS * 10, TimeUnit.MILLISECONDS) // 4000ms
            .readTimeout(HTTP_TIMEOUT_MS * 10, TimeUnit.MILLISECONDS) // 4000ms
            .writeTimeout(HTTP_TIMEOUT_MS * 10, TimeUnit.MILLISECONDS) // 4000ms
            .build();

    // Make the request - this should complete successfully
    try (Response response = longTimeoutClient.newCall(request).execute()) {
      // Verify we got a successful response
      assertThat(response.isSuccessful())
          .as("HTTP request should complete successfully without timeout")
          .isTrue();

      assertThat(response.code()).as("Should get 200 OK response").isEqualTo(200);

      // Verify response body contains trace data
      String responseBody = response.body().string();
      assertThat(responseBody)
          .as("Response should contain JSON-RPC trace result")
          .contains("jsonrpc")
          .contains("result");
    }

    // Verify the tracers were created and used
    CancellableOperationTracer spiedTracer = cancellableTracerSpy.get();
    assertThat(spiedTracer)
        .as("CancellableOperationTracer spy should have been created")
        .isNotNull();

    // Verify tracing methods were called
    verify(spiedTracer, times(46)).tracePreExecution(any(MessageFrame.class));

    // Most importantly: Verify NO interrupt exception was thrown
    RuntimeException capturedException = caughtInterruptException.get();
    assertThat(capturedException)
        .as("No interrupt exception should have been thrown in happy path")
        .isNull();
    assertThat(slowTracerRef.get().getTxCounter()).isEqualTo(1);
  }
}
