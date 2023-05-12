/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.handlers.batch;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_REQUEST;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

public class JsonRpcBatchExecutor {

  private static final String SPAN_CONTEXT = "span_context";
  final JsonRpcExecutor jsonRpcExecutor;
  final Tracer tracer;
  final RoutingContext ctx;
  final JsonRpcConfiguration jsonRpcConfiguration;
  private int resourceIntensiveRequestsCounter;

  public JsonRpcBatchExecutor(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final RoutingContext ctx,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    this.jsonRpcExecutor = jsonRpcExecutor;
    this.tracer = tracer;
    this.ctx = ctx;
    this.jsonRpcConfiguration = jsonRpcConfiguration;
  }

  public List<JsonRpcResponse> executeJsonArrayRequest(final JsonArray batchJsonRequest) {
    final List<JsonRpcResponse> jsonRpcBatchResponses = new ArrayList<>();
    for (int i = 0; i < batchJsonRequest.size(); i++) {
      final JsonObject jsonRequest;
      try {
        jsonRequest = batchJsonRequest.getJsonObject(i);
        if (canExecuteRequest(jsonRequest)) {
          JsonRpcResponse response = execute(jsonRequest);
          jsonRpcBatchResponses.add(response);
        } else {
          final Integer id = jsonRequest.getInteger("id", null);
          jsonRpcBatchResponses.add(new JsonRpcErrorResponse(id, EXCEEDS_RPC_MAX_BATCH_SIZE));
        }
      } catch (final ClassCastException e) {
        jsonRpcBatchResponses.add(new JsonRpcErrorResponse(null, INVALID_REQUEST));
      }
    }
    return jsonRpcBatchResponses;
  }

  private JsonRpcResponse execute(final JsonObject jsonRequest) {
    final Optional<User> user = ContextKey.AUTHENTICATED_USER.extractFrom(ctx, Optional::empty);
    final Context spanContext = ctx.get(SPAN_CONTEXT);
    return jsonRpcExecutor.execute(
        user,
        tracer,
        spanContext,
        () -> !ctx.response().closed(),
        jsonRequest,
        req -> req.mapTo(JsonRpcRequest.class));
  }

  private boolean canExecuteRequest(final JsonObject jsonRequest) {
    if (jsonRpcConfiguration.getMaxResourceIntensivePerBatchSize() > 0
        && isResourceIntensiveRequest(jsonRequest)) {
      return resourceIntensiveRequestsCounter++
          < jsonRpcConfiguration.getMaxResourceIntensivePerBatchSize();
    }
    return true;
  }

  private boolean isResourceIntensiveRequest(final JsonObject jsonRequest) {
    return jsonRpcConfiguration
        .getResourceIntensiveMethods()
        .contains(jsonRequest.getString("method"));
  }
}
