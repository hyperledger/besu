/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(VertxUnitRunner.class)
public class WebSocketRequestHandlerTest {

  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;

  private Vertx vertx;
  private WebSocketRequestHandler handler;
  private JsonRpcMethod jsonRpcMethodMock;
  private final Map<String, JsonRpcMethod> methods = new HashMap<>();

  @Before
  public void before(final TestContext context) {
    vertx = Vertx.vertx();

    jsonRpcMethodMock = mock(JsonRpcMethod.class);

    methods.put("eth_x", jsonRpcMethodMock);
    handler = new WebSocketRequestHandler(vertx, methods);
  }

  @After
  public void after(final TestContext context) {
    Mockito.reset(jsonRpcMethodMock);
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void handlerDeliversResponseSuccessfully(final TestContext context) {
    final Async async = context.async();

    final JsonObject requestJson = new JsonObject().put("id", 1).put("method", "eth_x");
    final JsonRpcRequest expectedRequest = requestJson.mapTo(WebSocketRpcRequest.class);
    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(expectedRequest.getId(), null);
    when(jsonRpcMethodMock.response(eq(expectedRequest))).thenReturn(expectedResponse);

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer(requestJson.toString())));

    async.awaitSuccess(WebSocketRequestHandlerTest.VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void jsonDecodeFailureShouldRespondInvalidRequest(final TestContext context) {
    final Async async = context.async();

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INVALID_REQUEST);

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              verifyZeroInteractions(jsonRpcMethodMock);
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer("")));

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void objectMapperFailureShouldRespondInvalidRequest(final TestContext context) {
    final Async async = context.async();

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INVALID_REQUEST);

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              verifyZeroInteractions(jsonRpcMethodMock);
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer("{}")));

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void absentMethodShouldRespondMethodNotFound(final TestContext context) {
    final Async async = context.async();

    final JsonObject requestJson =
        new JsonObject().put("id", 1).put("method", "eth_nonexistentMethod");
    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(1, JsonRpcError.METHOD_NOT_FOUND);

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer(requestJson.toString())));

    async.awaitSuccess(WebSocketRequestHandlerTest.VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void onExceptionProcessingRequestShouldRespondInternalError(final TestContext context) {
    final Async async = context.async();

    final JsonObject requestJson = new JsonObject().put("id", 1).put("method", "eth_x");
    final JsonRpcRequest expectedRequest = requestJson.mapTo(WebSocketRpcRequest.class);
    when(jsonRpcMethodMock.response(eq(expectedRequest))).thenThrow(new RuntimeException());
    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(1, JsonRpcError.INTERNAL_ERROR);

    final String websocketId = UUID.randomUUID().toString();

    vertx
        .eventBus()
        .consumer(websocketId)
        .handler(
            msg -> {
              context.assertEquals(Json.encode(expectedResponse), msg.body());
              async.complete();
            })
        .completionHandler(v -> handler.handle(websocketId, Buffer.buffer(requestJson.toString())));

    async.awaitSuccess(WebSocketRequestHandlerTest.VERTX_AWAIT_TIMEOUT_MILLIS);
  }
}
