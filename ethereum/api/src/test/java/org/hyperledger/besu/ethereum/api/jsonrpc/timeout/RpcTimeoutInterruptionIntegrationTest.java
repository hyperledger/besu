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

import org.hyperledger.besu.ethereum.api.jsonrpc.AbstractJsonRpcHttpServiceTest;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcHttpService;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.nat.NatService;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RpcTimeoutInterruptionIntegrationTest extends AbstractJsonRpcHttpServiceTest {

  @TempDir private Path testFolder;

  // Default server timeout from JsonRpcConfiguration.DEFAULT_HTTP_TIMEOUT_SEC (300 seconds)
  // We'll use a much shorter timeout for testing purposes
  private static final long SERVER_TIMEOUT_MS = 1000; // 1 second for test purposes
  private static final String TEST_METHOD_NAME = "test_slowMethod";

  private final AtomicInteger methodInvocations = new AtomicInteger(0);
  private final AtomicBoolean methodWasInterrupted = new AtomicBoolean(false);
  private volatile long methodDelayMs =
      SERVER_TIMEOUT_MS * 2; // Default: slow enough to cause timeout

  @BeforeEach
  public void confirmSetup() {
    // Parent's setup() is automatically called by JUnit before this
    // Now start the service with custom timeout configuration
    startService();
  }

  @Override
  protected void startService() {
    // Create config with custom timeout for testing
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setMaxBatchSize(10);
    config.setHttpTimeoutSec(SERVER_TIMEOUT_MS / 1000); // Convert ms to seconds

    final Map<String, JsonRpcMethod> methods = getRpcMethods(config, blockchainSetupUtil);
    final NatService natService = new NatService(java.util.Optional.empty());

    service =
        new JsonRpcHttpService(
            vertx,
            testFolder,
            config,
            new NoOpMetricsSystem(),
            natService,
            methods,
            HealthService.ALWAYS_HEALTHY,
            HealthService.ALWAYS_HEALTHY);
    service.start().join();

    client = new okhttp3.OkHttpClient();
    baseUrl = service.url();
  }

  @Override
  protected Map<String, JsonRpcMethod> getRpcMethods(
      final JsonRpcConfiguration config, final BlockchainSetupUtil blockchainSetupUtil) {
    final Map<String, JsonRpcMethod> methods = super.getRpcMethods(config, blockchainSetupUtil);

    // Add a custom test method that simulates slow execution
    methods.put(TEST_METHOD_NAME, new SlowTestMethod());

    return methods;
  }

  /**
   * A test RPC method that simulates slow execution and tracks interrupt status. This is a generic
   * test method (not tied to any specific RPC functionality) that allows us to test the timeout
   * mechanism.
   */
  private class SlowTestMethod implements JsonRpcMethod {
    @Override
    public String getName() {
      return TEST_METHOD_NAME;
    }

    @Override
    public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
      methodInvocations.incrementAndGet();

      try {
        // Simulate slow operation
        Thread.sleep(methodDelayMs);
        // If we get here, we weren't interrupted
        return new JsonRpcSuccessResponse(
            requestContext.getRequest().getId(), "completed successfully");
      } catch (InterruptedException e) {
        // Track that we were interrupted
        methodWasInterrupted.set(true);
        Thread.currentThread().interrupt(); // Restore interrupt status
        throw new RuntimeException("Method execution interrupted", e);
      }
    }
  }

  @Test
  public void shouldTimeoutSlowRpcMethod() throws Exception {
    // Reset state
    methodInvocations.set(0);
    methodWasInterrupted.set(false);
    methodDelayMs = SERVER_TIMEOUT_MS * 2; // Slow enough to exceed server timeout

    final String fixedBaseUrl = baseUrl.replace("localhost", "127.0.0.1");

    // Create request for our test method
    final String requestJson =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", TEST_METHOD_NAME);

    final RequestBody requestBody = RequestBody.create(requestJson, JSON);
    final Request request = new Request.Builder().url(fixedBaseUrl).post(requestBody).build();

    // Use a client with longer timeout than server to ensure server times out first
    final okhttp3.OkHttpClient client =
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(SERVER_TIMEOUT_MS * 5, TimeUnit.MILLISECONDS)
            .readTimeout(SERVER_TIMEOUT_MS * 5, TimeUnit.MILLISECONDS)
            .writeTimeout(SERVER_TIMEOUT_MS * 5, TimeUnit.MILLISECONDS)
            .build();

    // Execute request and verify timeout response
    try (Response response = client.newCall(request).execute()) {
      assertThat(response.code())
          .as("Should receive timeout HTTP status code")
          .isEqualTo(504); // GATEWAY_TIMEOUT from Vert.x TimeoutHandler

      String responseBody = response.body().string();

      // The Vert.x TimeoutHandler returns plain text, not JSON-RPC format
      assertThat(responseBody)
          .as("Response body should contain timeout message")
          .containsIgnoringCase("timeout");
    }

    // Verify the method was invoked
    assertThat(methodInvocations.get()).as("Method should have been invoked").isGreaterThan(0);

    // Wait a bit for interruption to propagate
    Thread.sleep(200);

    // Verify the method execution was interrupted
    assertThat(methodWasInterrupted.get())
        .as("Method execution should have been interrupted due to timeout")
        .isTrue();
  }

  @Test
  public void shouldCompleteSuccessfullyWhenNoTimeoutOccurs() throws Exception {
    // Reset state
    methodInvocations.set(0);
    methodWasInterrupted.set(false);
    methodDelayMs = 50; // Fast enough to complete within timeout

    final String fixedBaseUrl = baseUrl.replace("localhost", "127.0.0.1");

    final String requestJson =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":[],\"id\":1}", TEST_METHOD_NAME);

    final RequestBody requestBody = RequestBody.create(requestJson, JSON);
    final Request request = new Request.Builder().url(fixedBaseUrl).post(requestBody).build();

    final okhttp3.OkHttpClient client =
        new okhttp3.OkHttpClient.Builder()
            .connectTimeout(SERVER_TIMEOUT_MS * 5, TimeUnit.MILLISECONDS)
            .readTimeout(SERVER_TIMEOUT_MS * 5, TimeUnit.MILLISECONDS)
            .writeTimeout(SERVER_TIMEOUT_MS * 5, TimeUnit.MILLISECONDS)
            .build();

    try (Response response = client.newCall(request).execute()) {
      assertThat(response.isSuccessful())
          .as("HTTP request should complete successfully without timeout")
          .isTrue();

      assertThat(response.code()).as("Should get 200 OK response").isEqualTo(200);

      String responseBody = response.body().string();
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonResponse = mapper.readTree(responseBody);

      // Verify successful JSON-RPC response
      assertThat(jsonResponse.has("result")).as("Response should contain result field").isTrue();

      assertThat(jsonResponse.get("result").asText())
          .as("Result should indicate successful completion")
          .isEqualTo("completed successfully");
    }

    // Verify the method was invoked exactly once
    assertThat(methodInvocations.get())
        .as("Method should have been invoked exactly once")
        .isEqualTo(1);

    // Verify the method was NOT interrupted
    assertThat(methodWasInterrupted.get())
        .as("Method execution should not have been interrupted")
        .isFalse();
  }
}
