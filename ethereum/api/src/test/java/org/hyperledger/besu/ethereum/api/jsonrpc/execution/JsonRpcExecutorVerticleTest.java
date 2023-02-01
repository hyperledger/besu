/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hyperledger.besu.ethereum.api.jsonrpc.EventBusAddress.RPC_EXECUTE_ARRAY;
import static org.hyperledger.besu.ethereum.api.jsonrpc.EventBusAddress.RPC_EXECUTE_OBJECT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.handlers.NonBlockingJsonRpcExecutorHandler;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
class JsonRpcExecutorVerticleTest {
  NonBlockingJsonRpcExecutorHandler nonBlockingJsonRpcExecutorHandler;

  @Mock JsonRpcExecutor mockJsonRpcExecutor;
  @Mock JsonRpcConfiguration mockJsonRpcConfiguration;

  @BeforeEach
  void setUp(final Vertx vertx) {
    final JsonRpcExecutorVerticle jsonRpcExecutorVerticle =
        new JsonRpcExecutorVerticle(
            mockJsonRpcExecutor, mock(Tracer.class), mockJsonRpcConfiguration);

    nonBlockingJsonRpcExecutorHandler =
        new NonBlockingJsonRpcExecutorHandler(
            vertx, List.of(jsonRpcExecutorVerticle), mockJsonRpcExecutor, mock(Tracer.class));
    await().atMost(5, TimeUnit.SECONDS).until(() -> vertx.deploymentIDs().size() == 1);
  }

  @AfterEach
  void tearDown(final Vertx vertx) {
    nonBlockingJsonRpcExecutorHandler.stop();
    await().atMost(5, TimeUnit.SECONDS).until(() -> vertx.deploymentIDs().size() == 0);
  }

  @Test
  void correctJsonResponseOfObjectRequestShouldBeReturned(
      final Vertx vertx, final VertxTestContext testContext) throws Throwable {
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse("42", "successResult");
    when(mockJsonRpcExecutor.execute(any(), any(), any(), any(), any(), any()))
        .thenReturn(expectedResponse);
    vertx
        .eventBus()
        .request(RPC_EXECUTE_OBJECT.getAddress(), mock(JsonRpcExecutorObjectRequest.class))
        .onSuccess(
            actualResponse -> {
              assertThat(actualResponse.body()).isEqualTo(expectedResponse);
              testContext.completeNow();
            });

    assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

  @Test
  void exceedingMaxBatchSizeShouldCauseFailure(
      final Vertx vertx, final VertxTestContext testContext) throws Throwable {
    when(mockJsonRpcConfiguration.getMaxBatchSize()).thenReturn(1);
    JsonRpcExecutorArrayRequest mockRequest = mock(JsonRpcExecutorArrayRequest.class);
    when(mockRequest.getJsonArray())
        .thenReturn(new JsonArray(List.of(mock(JsonObject.class), mock(JsonObject.class))));

    vertx
        .eventBus()
        .request(RPC_EXECUTE_ARRAY.getAddress(), mockRequest)
        .onFailure(
            e -> {
              assertThat(((ReplyException) e).failureCode())
                  .isEqualTo(EXCEEDS_RPC_MAX_BATCH_SIZE.getCode());
              assertThat(e.getMessage()).isEqualTo(EXCEEDS_RPC_MAX_BATCH_SIZE.getMessage());
              testContext.completeNow();
            });

    assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

  @Test
  void correctJsonResponseOfArrayRequestShouldBeReturned(
      final Vertx vertx, final VertxTestContext testContext) throws Throwable {
    when(mockJsonRpcConfiguration.getMaxBatchSize()).thenReturn(2);

    JsonRpcExecutorArrayRequest mockRequest = mock(JsonRpcExecutorArrayRequest.class);
    when(mockRequest.getJsonArray())
        .thenReturn(new JsonArray(List.of(mock(JsonObject.class), mock(JsonObject.class))));

    final JsonRpcResponse expectedResponse1 = new JsonRpcSuccessResponse("42", "successResult");
    final JsonRpcResponse expectedResponse2 = new JsonRpcSuccessResponse("43", "successResult2");

    when(mockJsonRpcExecutor.execute(any(), any(), any(), any(), any(), any()))
        .thenReturn(expectedResponse1, expectedResponse2);

    vertx
        .eventBus()
        .request(RPC_EXECUTE_ARRAY.getAddress(), mockRequest)
        .onSuccess(
            actualReply -> {
              final JsonRpcResponse[] actualJsonRpcBatchResponses =
                  (JsonRpcResponse[]) actualReply.body();

              assertThat(actualJsonRpcBatchResponses.length).isEqualTo(2);
              assertThat(actualJsonRpcBatchResponses[0]).isEqualTo(expectedResponse1);
              assertThat(actualJsonRpcBatchResponses[1]).isEqualTo(expectedResponse2);
              testContext.completeNow();
            });

    assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

  @Test
  void exceptionDuringProcessingOfArrayRequestShouldReturnInternalError(
      final Vertx vertx, final VertxTestContext testContext) throws Throwable {
    when(mockJsonRpcConfiguration.getMaxBatchSize()).thenReturn(2);
    JsonRpcExecutorArrayRequest mockRequest = mock(JsonRpcExecutorArrayRequest.class);
    when(mockRequest.getJsonArray())
        .thenReturn(new JsonArray(List.of(mock(JsonObject.class), mock(JsonObject.class))));

    when(mockJsonRpcExecutor.execute(any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException());

    vertx
        .eventBus()
        .request(RPC_EXECUTE_ARRAY.getAddress(), mockRequest)
        .onFailure(
            e -> {
              assertThat(((ReplyException) e).failureCode()).isEqualTo(INTERNAL_ERROR.getCode());
              testContext.completeNow();
            });

    assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

  @Test
  void anInvalidJsonObjectRequestShouldReturnInvalidParameter(
      final Vertx vertx, final VertxTestContext testContext) throws Throwable {
    when(mockJsonRpcConfiguration.getMaxBatchSize()).thenReturn(2);
    JsonRpcExecutorArrayRequest mockRequest = mock(JsonRpcExecutorArrayRequest.class);
    when(mockRequest.getJsonArray())
        .thenReturn(new JsonArray(List.of("invalid json object", mock(JsonObject.class))));

    final JsonRpcResponse expectedResponse2 = new JsonRpcSuccessResponse("42", "successResult");
    when(mockJsonRpcExecutor.execute(any(), any(), any(), any(), any(), any()))
        .thenReturn(expectedResponse2);

    vertx
        .eventBus()
        .request(RPC_EXECUTE_ARRAY.getAddress(), mockRequest)
        .onSuccess(
            actualReply -> {
              final JsonRpcResponse[] actualJsonRpcBatchResponses =
                  (JsonRpcResponse[]) actualReply.body();

              assertThat(actualJsonRpcBatchResponses.length).isEqualTo(2);

              assertThat(actualJsonRpcBatchResponses[0]).isOfAnyClassIn(JsonRpcErrorResponse.class);
              assertThat(((JsonRpcErrorResponse) actualJsonRpcBatchResponses[0]).getId()).isNull();
              assertThat(((JsonRpcErrorResponse) actualJsonRpcBatchResponses[0]).getError())
                  .isEqualTo(INVALID_REQUEST);

              assertThat(actualJsonRpcBatchResponses[1]).isEqualTo(expectedResponse2);
              testContext.completeNow();
            });
    assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }
}
