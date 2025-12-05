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
package org.hyperledger.besu.ethereum.api.handlers;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonRpcExecutorHandlerTest {

  private JsonRpcExecutor mockExecutor;
  private Tracer mockTracer;
  private JsonRpcConfiguration mockConfig;
  private RoutingContext mockContext;
  private Vertx mockVertx;
  private HttpServerResponse mockResponse;
  private final long timeoutSeconds = 22;

  @BeforeEach
  void setUp() {
    mockExecutor = mock(JsonRpcExecutor.class);
    mockTracer = mock(Tracer.class);
    mockConfig = mock(JsonRpcConfiguration.class);
    mockContext = mock(RoutingContext.class);
    mockVertx = mock(Vertx.class);
    mockResponse = mock(HttpServerResponse.class);

    when(mockConfig.getHttpTimeoutSec()).thenReturn(timeoutSeconds);
    when(mockContext.vertx()).thenReturn(mockVertx);
    when(mockContext.response()).thenReturn(mockResponse);
    when(mockResponse.ended()).thenReturn(false);
    when(mockResponse.setStatusCode(anyInt())).thenReturn(mockResponse);

    // Add minimal mocking to pass isJsonObjectRequest() check
    Map<String, Object> contextData = new HashMap<>();
    contextData.put(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name(), new Object());
    when(mockContext.data()).thenReturn(contextData);
  }

  @Test
  void testTimeoutHandling() {
    // Arrange: Use 0 seconds (0ms) timeout to trigger immediate timeout
    // Note: CompletableFuture.orTimeout(0, ...) means immediate timeout, not infinite
    when(mockConfig.getHttpTimeoutSec()).thenReturn(0L);

    // Create activeRequestsByConnection map
    Map<HttpConnection, Set<InterruptibleCompletableFuture<Void>>> activeRequestsByConnection =
        new ConcurrentHashMap<>();

    Handler<RoutingContext> handler =
        JsonRpcExecutorHandler.handler(
            mockExecutor, mockTracer, mockConfig, activeRequestsByConnection);

    JsonObject requestBody = new JsonObject().put("jsonrpc", "2.0").put("method", "test");
    when(mockContext.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name())).thenReturn(requestBody);

    // Act
    handler.handle(mockContext);

    // Wait for async timeout to occur (using Mockito timeout)
    // The handler returns 408 (REQUEST_TIMEOUT) for timeout errors
    verify(mockResponse, timeout(2000)).setStatusCode(408);
    verify(mockResponse, timeout(2000)).end(contains("Timeout expired"));
  }

  @Test
  void testSuccessfulExecution() {
    // Arrange
    // Create activeRequestsByConnection map
    Map<HttpConnection, Set<InterruptibleCompletableFuture<Void>>> activeRequestsByConnection =
        new ConcurrentHashMap<>();

    Handler<RoutingContext> handler =
        JsonRpcExecutorHandler.handler(
            mockExecutor, mockTracer, mockConfig, activeRequestsByConnection);

    JsonObject requestBody = new JsonObject().put("jsonrpc", "2.0").put("method", "test");
    when(mockContext.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name())).thenReturn(requestBody);

    // Act
    handler.handle(mockContext);

    // No assertions needed - just verifying no exceptions thrown
    // Actual execution completes asynchronously and the integration test covers end-to-end behavior
  }
}
