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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.EXCEEDS_RPC_MAX_RESOURCE_INTENSIVE_BATCH_SIZE;
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

/**
 * Executes batches of JSON RPC requests, with special handling for resource-intensive requests.
 * Keeps track of the number of resource-intensive requests processed in a batch, and rejects any
 * that exceed a configured limit.
 */
public class JsonRpcBatchExecutor {

  private static final String SPAN_CONTEXT = "span_context";
  final JsonRpcExecutor jsonRpcExecutor;
  final Tracer tracer;
  final RoutingContext ctx;
  final JsonRpcConfiguration jsonRpcConfiguration;
  private int resourceIntensiveRequestsCounter;

  /**
   * Creates a new JsonRpcBatchExecutor.
   *
   * @param jsonRpcExecutor The executor used to process the JSON RPC requests.
   * @param tracer The tracer used for monitoring and debugging purposes.
   * @param ctx The context of the routing, containing information about the HTTP request and
   *     response.
   * @param jsonRpcConfiguration The configuration for JSON RPC operations, including the maximum
   *     batch size for resource-intensive requests.
   */
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

  /**
   * Executes a batch of JSON RPC requests.
   *
   * @param rpcRequestBatch A JsonArray containing individual JSON RPC requests.
   * @return A List of JsonRpcResponse objects, each corresponding to the result of an individual
   *     request.
   */
  public List<JsonRpcResponse> executeRpcRequestBatch(final JsonArray rpcRequestBatch) {
    final List<JsonRpcResponse> rpcResponseList = new ArrayList<>();

    for (int i = 0; i < rpcRequestBatch.size(); i++) {
      try {
        final JsonObject individualRpcRequest = rpcRequestBatch.getJsonObject(i);
        rpcResponseList.add(processRpcRequest(individualRpcRequest));
      } catch (final ClassCastException exception) {
        // In case of invalid request format, add an error response
        rpcResponseList.add(new JsonRpcErrorResponse(null, INVALID_REQUEST));
      }
    }

    return rpcResponseList;
  }

  /**
   * Processes an individual RPC request. Determines if the request is resource-intensive and
   * handles it accordingly.
   *
   * @param rpcRequest JsonObject representing the RPC request.
   * @return A JsonRpcResponse corresponding to the result of the request.
   */
  private JsonRpcResponse processRpcRequest(final JsonObject rpcRequest) {
    if (isResourceIntensiveRequest(rpcRequest)) {
      return handleResourceIntensiveRequest(rpcRequest);
    } else {
      return executeRpcRequest(rpcRequest);
    }
  }

  /**
   * Processes a resource-intensive RPC request. Checks if the request exceeds the limit per batch
   * and handles it accordingly.
   *
   * @param rpcRequest JsonObject representing the RPC request.
   * @return A JsonRpcResponse corresponding to the result of the request.
   */
  private JsonRpcResponse handleResourceIntensiveRequest(final JsonObject rpcRequest) {
    if (canProcessResourceIntensiveRequest(rpcRequest)) {
      resourceIntensiveRequestsCounter++;
      return executeRpcRequest(rpcRequest);
    } else {
      // If the request is resource-intensive and the limit for such requests has been
      // exceeded, retrieve the request ID and add an error response
      final Integer requestId = rpcRequest.getInteger("id", null);
      return new JsonRpcErrorResponse(requestId, EXCEEDS_RPC_MAX_RESOURCE_INTENSIVE_BATCH_SIZE);
    }
  }

  /**
   * Checks if a given resource-intensive RPC request can be processed. Validates if the request
   * does not exceed the limit for resource-intensive requests per batch.
   *
   * @param rpcRequest JsonObject representing the RPC request.
   * @return A boolean indicating whether the resource-intensive request can be processed.
   */
  private boolean canProcessResourceIntensiveRequest(final JsonObject rpcRequest) {
    int maxResourceIntensiveRequestsPerBatch =
        jsonRpcConfiguration.getMaxResourceIntensivePerBatchSize();

    if (maxResourceIntensiveRequestsPerBatch > 0 && isResourceIntensiveRequest(rpcRequest)) {
      return resourceIntensiveRequestsCounter < maxResourceIntensiveRequestsPerBatch;
    }

    return true;
  }

  /**
   * Determines if a given RPC request is resource-intensive. Checks if the method in the request is
   * part of the configured resource-intensive methods.
   *
   * @param rpcRequest JsonObject representing the RPC request.
   * @return A boolean indicating whether the request is resource-intensive.
   */
  private boolean isResourceIntensiveRequest(final JsonObject rpcRequest) {
    return jsonRpcConfiguration
        .getResourceIntensiveMethods()
        .contains(rpcRequest.getString("method"));
  }

  /**
   * Executes a given JSON RPC request.
   *
   * @param rpcRequest JsonObject representing the RPC request.
   * @return A JsonRpcResponse corresponding to the result of the request.
   */
  private JsonRpcResponse executeRpcRequest(final JsonObject rpcRequest) {
    final Optional<User> authenticatedUser =
        ContextKey.AUTHENTICATED_USER.extractFrom(ctx, Optional::empty);
    final Context requestSpanContext = ctx.get(SPAN_CONTEXT);

    // Execute the RPC request
    return jsonRpcExecutor.execute(
        authenticatedUser,
        tracer,
        requestSpanContext,
        () -> !ctx.response().closed(),
        rpcRequest,
        request -> request.mapTo(JsonRpcRequest.class));
  }
}
