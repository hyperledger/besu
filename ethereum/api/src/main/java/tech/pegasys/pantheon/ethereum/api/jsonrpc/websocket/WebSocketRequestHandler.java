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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.authentication.AuthenticationService;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.authentication.AuthenticationUtils;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;

import java.util.Map;
import java.util.Optional;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.auth.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebSocketRequestHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx;
  private final Map<String, JsonRpcMethod> methods;

  public WebSocketRequestHandler(final Vertx vertx, final Map<String, JsonRpcMethod> methods) {
    this.vertx = vertx;
    this.methods = methods;
  }

  public void handle(final String id, final Buffer buffer) {
    handle(Optional.empty(), id, buffer, Optional.empty());
  }

  public void handle(
      final Optional<AuthenticationService> authenticationService,
      final String id,
      final Buffer buffer,
      final Optional<User> user) {
    vertx.executeBlocking(
        future -> {
          final WebSocketRpcRequest request;
          try {
            request = buffer.toJsonObject().mapTo(WebSocketRpcRequest.class);
          } catch (final IllegalArgumentException | DecodeException e) {
            LOG.debug("Error mapping json to WebSocketRpcRequest", e);
            future.complete(new JsonRpcErrorResponse(null, JsonRpcError.INVALID_REQUEST));
            return;
          }

          if (!methods.containsKey(request.getMethod())) {
            future.complete(
                new JsonRpcErrorResponse(request.getId(), JsonRpcError.METHOD_NOT_FOUND));
            LOG.debug("Can't find method {}", request.getMethod());
            return;
          }
          final JsonRpcMethod method = methods.get(request.getMethod());
          try {
            LOG.debug("WS-RPC request -> {}", request.getMethod());
            request.setConnectionId(id);
            if (AuthenticationUtils.isPermitted(authenticationService, user, method)) {
              future.complete(method.response(request));
            } else {
              future.complete(
                  new JsonRpcUnauthorizedResponse(request.getId(), JsonRpcError.UNAUTHORIZED));
            }
          } catch (final Exception e) {
            LOG.error(JsonRpcError.INTERNAL_ERROR.getMessage(), e);
            future.complete(new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR));
          }
        },
        result -> {
          if (result.succeeded()) {
            replyToClient(id, Json.encodeToBuffer(result.result()));
          } else {
            replyToClient(
                id,
                Json.encodeToBuffer(new JsonRpcErrorResponse(null, JsonRpcError.INTERNAL_ERROR)));
          }
        });
  }

  private void replyToClient(final String id, final Buffer request) {
    vertx.eventBus().send(id, request.toString());
  }
}
