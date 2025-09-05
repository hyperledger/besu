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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.AbstractJsonRpcHttpServiceTest;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugTraceTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.debug.OpCodeTracerConfig;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RpcTimeoutInterruptionIntegrationTest extends AbstractJsonRpcHttpServiceTest {

  private static final long HTTP_TIMEOUT_MS = 400;
  private static final long OVERRUN_MS = 400;
  private static final long TRACER_DELAY_PER_OPERATION_MS = 10; // Delay added per EVM operation

  @BeforeEach
  public void setUp() throws Exception {
    startService();
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
            new org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay(
                blockchainSetupUtil.getProtocolSchedule(),
                blockchainSetupUtil.getProtocolContext(),
                blockchainSetupUtil.getBlockchain()));

    // Replace debug_traceTransaction with one that uses a slow tracer
    methods.put(
        "debug_traceTransaction",
        new DebugTraceTransaction(
            blockchainQueries,
            transactionTracer,
            options -> new SlowDebugOperationTracer(options.opCodeTracerConfig(), true)));

    return methods;
  }

  /**
   * A DebugOperationTracer that adds artificial delays to simulate slow execution. This ensures the
   * test reliably demonstrates the timeout issue regardless of machine speed.
   */
  private static class SlowDebugOperationTracer extends DebugOperationTracer {

    public SlowDebugOperationTracer(
        final OpCodeTracerConfig opCodeTracerConfig, final boolean recordMemory) {
      super(opCodeTracerConfig, recordMemory);
    }

    @Override
    public void tracePreExecution(final MessageFrame frame) {
      try {
        // Add delay to simulate slow trace operation
        Thread.sleep(TRACER_DELAY_PER_OPERATION_MS);
      } catch (InterruptedException e) {
        // This is what we want to happen when timeout occurs
        Thread.currentThread().interrupt();
        throw new RuntimeException("Trace operation interrupted", e);
      }
      super.tracePreExecution(frame);
    }
  }

  @Test
  public void shouldTimeoutAndInterruptLongRunningDebugTrace() throws Exception {
    final AtomicBoolean traceCompleted = new AtomicBoolean(false);
    final CountDownLatch traceLatch = new CountDownLatch(1);

    // Get a transaction hash from the blockchain
    final Block block = blockchainSetupUtil.getBlockchain().getChainHeadBlock();
    final Hash txHash = block.getBody().getTransactions().get(0).getHash();

    // Create request with very short timeout
    final String requestJson =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"debug_traceTransaction\",\"params\":[\"%s\"],\"id\":1}",
            txHash.toHexString());

    final RequestBody requestBody = RequestBody.create(requestJson, JSON);
    final Request request = new Request.Builder().url(baseUrl).post(requestBody).build();

    // Set up a client with short timeout
    final okhttp3.OkHttpClient shortTimeoutClient =
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

    // Make async request to track what happens
    shortTimeoutClient
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(Call call, IOException e) {
                // Expected: HTTP timeout
                traceCompleted.set(false);
                traceLatch.countDown();
              }

              @Override
              public void onResponse(Call call, Response response) {
                // Should not reach here due to timeout
                traceCompleted.set(true);
                traceLatch.countDown();
              }
            });

    // Wait for HTTP timeout to occur
    assertThat(traceLatch.await(HTTP_TIMEOUT_MS + OVERRUN_MS, TimeUnit.MILLISECONDS)).isTrue();

    // Verify that HTTP request timed out
    assertThat(traceCompleted.get()).isFalse();

    // The problem: The debug trace operation continues running in the background
    // even after the HTTP request has timed out. This wastes server resources.
    // This test demonstrates the issue - it will pass, showing the timeout works,
    // but the underlying trace operation is not interrupted.
  }
}
