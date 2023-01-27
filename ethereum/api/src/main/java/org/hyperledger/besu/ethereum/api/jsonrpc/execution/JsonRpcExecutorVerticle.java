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

import static org.hyperledger.besu.ethereum.api.jsonrpc.EventBusAddress.RPC_EXECUTE_ARRAY;
import static org.hyperledger.besu.ethereum.api.jsonrpc.EventBusAddress.RPC_EXECUTE_OBJECT;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_REQUEST;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcExecutorVerticle extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcExecutorVerticle.class);

  private final JsonRpcExecutor jsonRpcExecutor;
  private final Tracer tracer;
  final JsonRpcConfiguration jsonRpcConfiguration;

  public JsonRpcExecutorVerticle(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    this.jsonRpcExecutor = jsonRpcExecutor;
    this.tracer = tracer;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
  }

  @Override
  public void start() {
    vertx
        .eventBus()
        .consumer(RPC_EXECUTE_OBJECT.getAddress(), this::handleJsonRpcExecutorObjectRequest);
    vertx
        .eventBus()
        .consumer(RPC_EXECUTE_ARRAY.getAddress(), this::handleJsonRpcExecutorArrayRequest);
  }

  private void handleJsonRpcExecutorObjectRequest(
      final Message<JsonRpcExecutorObjectRequest> requestMessage) {
    final JsonRpcExecutorObjectRequest request = requestMessage.body();
    LOG.trace("Received executorRequest {}", request);

    final JsonRpcResponse jsonRpcResponse =
        jsonRpcExecutor.execute(
            request.getOptionalUser(),
            tracer,
            request.getSpanContext(),
            request.getAlive(),
            request.getJsonObject(),
            req -> req.mapTo(JsonRpcRequest.class));

    requestMessage.reply(jsonRpcResponse);
  }

  private void handleJsonRpcExecutorArrayRequest(
      final Message<JsonRpcExecutorArrayRequest> requestMessage) {
    final JsonRpcExecutorArrayRequest request = requestMessage.body();
    final JsonArray jsonArray = request.getJsonArray();

    if (jsonRpcConfiguration.getMaxBatchSize() > 0
        && jsonArray.size() > jsonRpcConfiguration.getMaxBatchSize()) {
      requestMessage.fail(
          EXCEEDS_RPC_MAX_BATCH_SIZE.getCode(), EXCEEDS_RPC_MAX_BATCH_SIZE.getMessage());
      return;
    }

    JsonRpcResponse[] jsonRpcBatchResponses = new JsonRpcResponse[jsonArray.size()];
    try {
      for (int i = 0; i < jsonArray.size(); i++) {
        final JsonObject jsonRequest;
        try {
          jsonRequest = jsonArray.getJsonObject(i);
        } catch (ClassCastException e) {
          jsonRpcBatchResponses[i] = new JsonRpcErrorResponse(null, INVALID_REQUEST);
          continue;
        }
        jsonRpcBatchResponses[i] =
            jsonRpcExecutor.execute(
                request.getOptionalUser(),
                tracer,
                request.getSpanContext(),
                request.getAlive(),
                jsonRequest,
                req -> req.mapTo(JsonRpcRequest.class));
      }
    } catch (RuntimeException e) {
      requestMessage.fail(INTERNAL_ERROR.getCode(), e.getMessage());
      return;
    }

    requestMessage.reply(jsonRpcBatchResponses);
  }
}
