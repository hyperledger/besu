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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static java.util.stream.Collectors.toList;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_REQUEST;

import org.hyperledger.besu.ethereum.api.handlers.IsAliveHandler;
import org.hyperledger.besu.ethereum.api.handlers.RpcMethodTimeoutException;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketRequestHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx;
  private final Map<String, JsonRpcMethod> methods;
  final EthScheduler ethScheduler;
  private final long timeoutSec;

  public WebSocketRequestHandler(
      final Vertx vertx,
      final Map<String, JsonRpcMethod> methods,
      final EthScheduler ethScheduler,
      final long timeoutSec) {
    this.vertx = vertx;
    this.methods = methods;
    this.ethScheduler = ethScheduler;
    this.timeoutSec = timeoutSec;
  }

  public void handle(final String id, final String payload) {
    handle(Optional.empty(), id, payload, Optional.empty());
  }

  public void handle(
      final Optional<AuthenticationService> authenticationService,
      final String id,
      final String payload,
      final Optional<User> user) {
    vertx.executeBlocking(
        executeHandler(authenticationService, id, payload, user), false, resultHandler(id));
  }

  private Handler<Promise<Object>> executeHandler(
      final Optional<AuthenticationService> authenticationService,
      final String id,
      final String payload,
      final Optional<User> user) {
    return future -> {
      final String json = payload.trim();
      if (!json.isEmpty() && json.charAt(0) == '{') {
        try {
          handleSingleRequest(authenticationService, id, user, future, getRequest(payload));
        } catch (final IllegalArgumentException | DecodeException e) {
          LOG.debug("Error mapping json to WebSocketRpcRequest", e);
          future.complete(new JsonRpcErrorResponse(null, JsonRpcError.INVALID_REQUEST));
          return;
        }
      } else if (json.length() == 0) {
        future.complete(errorResponse(null, INVALID_REQUEST));
        return;
      } else {
        final JsonArray jsonArray = new JsonArray(json);
        if (jsonArray.size() < 1) {
          future.complete(errorResponse(null, INVALID_REQUEST));
          return;
        }
        // handle batch request
        LOG.debug("batch request size {}", jsonArray.size());
        handleJsonBatchRequest(authenticationService, id, jsonArray, user);
      }
    };
  }

  private JsonRpcResponse process(
      final Optional<AuthenticationService> authenticationService,
      final String id,
      final Optional<User> user,
      final WebSocketRpcRequest requestBody) {

    if (!methods.containsKey(requestBody.getMethod())) {
      LOG.debug("Can't find method {}", requestBody.getMethod());
      return new JsonRpcErrorResponse(requestBody.getId(), JsonRpcError.METHOD_NOT_FOUND);
    }
    final JsonRpcMethod method = methods.get(requestBody.getMethod());
    try {
      LOG.debug("WS-RPC request -> {}", requestBody.getMethod());
      requestBody.setConnectionId(id);
      if (AuthenticationUtils.isPermitted(authenticationService, user, method)) {
        final JsonRpcRequestContext requestContext =
            new JsonRpcRequestContext(
                requestBody, user, new IsAliveHandler(ethScheduler, timeoutSec));
        return method.response(requestContext);
      } else {
        return new JsonRpcUnauthorizedResponse(requestBody.getId(), JsonRpcError.UNAUTHORIZED);
      }
    } catch (final InvalidJsonRpcParameters e) {
      LOG.debug("Invalid Params", e);
      return new JsonRpcErrorResponse(requestBody.getId(), JsonRpcError.INVALID_PARAMS);
    } catch (final RpcMethodTimeoutException e) {
      LOG.error(JsonRpcError.TIMEOUT_ERROR.getMessage(), e);
      return new JsonRpcErrorResponse(requestBody.getId(), JsonRpcError.TIMEOUT_ERROR);
    } catch (final Exception e) {
      LOG.error(JsonRpcError.INTERNAL_ERROR.getMessage(), e);
      return new JsonRpcErrorResponse(requestBody.getId(), JsonRpcError.INTERNAL_ERROR);
    }
  }

  private void handleSingleRequest(
      final Optional<AuthenticationService> authenticationService,
      final String id,
      final Optional<User> user,
      final Promise<Object> future,
      final WebSocketRpcRequest requestBody) {
    future.complete(process(authenticationService, id, user, requestBody));
  }

  @SuppressWarnings("rawtypes")
  private void handleJsonBatchRequest(
      final Optional<AuthenticationService> authenticationService,
      final String id,
      final JsonArray jsonArray,
      final Optional<User> user) {
    // Interpret json as rpc request
    final List<Future> responses =
        jsonArray.stream()
            .map(
                obj -> {
                  if (!(obj instanceof JsonObject)) {
                    return Future.succeededFuture(errorResponse(null, INVALID_REQUEST));
                  }

                  final JsonObject req = (JsonObject) obj;
                  return vertx.<JsonRpcResponse>executeBlocking(
                      future ->
                          future.complete(
                              process(
                                  authenticationService, id, user, getRequest(req.toString()))));
                })
            .collect(toList());

    CompositeFuture.all(responses)
        .onComplete(
            (res) -> {
              final JsonRpcResponse[] completed =
                  res.result().list().stream()
                      .map(JsonRpcResponse.class::cast)
                      .filter(this::isNonEmptyResponses)
                      .toArray(JsonRpcResponse[]::new);

              vertx.eventBus().send(id, Json.encode(completed));
            });
  }

  private WebSocketRpcRequest getRequest(final String payload) {
    return Json.decodeValue(payload, WebSocketRpcRequest.class);
  }

  private Handler<AsyncResult<Object>> resultHandler(final String id) {
    return result -> {
      if (result.succeeded()) {
        replyToClient(id, Json.encodeToBuffer(result.result()));
      } else {
        replyToClient(
            id, Json.encodeToBuffer(new JsonRpcErrorResponse(null, JsonRpcError.INTERNAL_ERROR)));
      }
    };
  }

  private void replyToClient(final String id, final Buffer request) {
    vertx.eventBus().send(id, request.toString());
  }

  private JsonRpcResponse errorResponse(final Object id, final JsonRpcError error) {
    return new JsonRpcErrorResponse(id, error);
  }

  private boolean isNonEmptyResponses(final JsonRpcResponse result) {
    return result.getType() != JsonRpcResponseType.NONE;
  }
}
