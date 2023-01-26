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

import static org.hyperledger.besu.ethereum.api.jsonrpc.EventBusAddress.RPC_EXECUTE_ARRAY;
import static org.hyperledger.besu.ethereum.api.jsonrpc.EventBusAddress.RPC_EXECUTE_OBJECT;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonResponseStreamer;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutorArrayRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutorObjectRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutorVerticle;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcNoResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;
import org.hyperledger.besu.util.vertx.GenericMessageCodec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentelemetry.context.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NonBlockingJsonRpcExecutorHandler implements Handler<RoutingContext> {

  private static final Logger LOG =
      LoggerFactory.getLogger(NonBlockingJsonRpcExecutorHandler.class);
  private static final String SPAN_CONTEXT = "span_context";
  private static final String APPLICATION_JSON = "application/json";
  private static final ObjectMapper JSON_OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module()); // Handle JDK8 Optionals (de)serialization
  private static final ObjectWriter JSON_OBJECT_WRITER =
      JSON_OBJECT_MAPPER
          .writerWithDefaultPrettyPrinter()
          .without(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM)
          .with(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

  private final List<String> verticleDeploymentIds = new ArrayList<>();
  private final Vertx vertx;

  public NonBlockingJsonRpcExecutorHandler(
      final Vertx vertx, final List<JsonRpcExecutorVerticle> jsonRpcExecutorVerticles) {
    this.vertx = vertx;

    try {
      vertx
          .eventBus()
          .registerDefaultCodec(
              JsonRpcExecutorObjectRequest.class,
              new GenericMessageCodec<>(JsonRpcExecutorObjectRequest.class, LOG))
          .registerDefaultCodec(
              JsonRpcExecutorArrayRequest.class,
              new GenericMessageCodec<>(JsonRpcExecutorArrayRequest.class, LOG));
      vertx
          .eventBus()
          .registerDefaultCodec(
              JsonRpcSuccessResponse.class,
              new GenericMessageCodec<>(JsonRpcSuccessResponse.class, LOG))
          .registerDefaultCodec(
              JsonRpcErrorResponse.class,
              new GenericMessageCodec<>(JsonRpcErrorResponse.class, LOG))
          .registerDefaultCodec(
              JsonRpcNoResponse.class, new GenericMessageCodec<>(JsonRpcNoResponse.class, LOG))
          .registerDefaultCodec(
              JsonRpcUnauthorizedResponse.class,
              new GenericMessageCodec<>(JsonRpcUnauthorizedResponse.class, LOG))
          .registerDefaultCodec(
              JsonRpcResponse[].class, new GenericMessageCodec<>(JsonRpcResponse[].class, LOG));
    } catch (final IllegalStateException ignored) {
      // can be ignored
      // is thrown when registerDefaultCodec is called more than once, which can happen during tests
    }

    jsonRpcExecutorVerticles.forEach(
        jsonRpcExecutorVerticle ->
            vertx.deployVerticle(
                jsonRpcExecutorVerticle,
                new DeploymentOptions().setWorker(true),
                deploymentResult -> {
                  if (deploymentResult.succeeded()) {
                    verticleDeploymentIds.add(deploymentResult.result());
                  }
                }));
  }

  @Override
  public void handle(final RoutingContext ctx) {
    HttpServerResponse response = ctx.response();
    try {
      final Optional<User> user = ContextKey.AUTHENTICATED_USER.extractFrom(ctx, Optional::empty);
      final Context spanContext = ctx.get(SPAN_CONTEXT);
      response = response.putHeader("Content-Type", APPLICATION_JSON);

      final boolean isRequestBodyAnObject =
          ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
      final boolean isRequestBodyAnArray =
          ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());

      if (!isRequestBodyAnObject && !isRequestBodyAnArray) {
        handleJsonRpcError(ctx, null, JsonRpcError.PARSE_ERROR);
        return;
      }

      if (isRequestBodyAnArray) {
        processRequestBodyAsArray(ctx, response, user, spanContext);
        return;
      }

      processRequestBodyAsObject(ctx, response, user, spanContext);
    } catch (final IOException | RuntimeException e) {
      LOG.error("RPC call failed: {}", e.getMessage());
      handleJsonRpcError(ctx, null, JsonRpcError.INTERNAL_ERROR);
    }
  }

  public void stop() {
    verticleDeploymentIds.forEach(deploymentId -> vertx.undeploy(deploymentId));
  }

  private void processRequestBodyAsObject(
      final RoutingContext ctx,
      final HttpServerResponse response,
      final Optional<User> user,
      final Context spanContext)
      throws IOException {
    JsonObject jsonRequest = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
    lazyTraceLogger(jsonRequest::toString);

    vertx
        .eventBus()
        .request(
            RPC_EXECUTE_OBJECT.getAddress(),
            new JsonRpcExecutorObjectRequest(
                user, spanContext, () -> !ctx.response().closed(), jsonRequest))
        .onSuccess(
            msg -> {
              try {
                handleRpcExecutorObjectResponse(ctx, response, (JsonRpcResponse) msg.body());
              } catch (IOException e) {
                LOG.error(
                    "Error while processing {}: {}", getRequestMethodName(ctx), e.getMessage());
                handleJsonRpcError(ctx, getRequestId(ctx), JsonRpcError.INTERNAL_ERROR);
              }
            })
        .onFailure(
            e -> {
              LOG.error("Error while processing {}: {}", getRequestMethodName(ctx), e.getMessage());
              handleJsonRpcError(ctx, getRequestId(ctx), JsonRpcError.INTERNAL_ERROR);
            });
  }

  private void handleRpcExecutorObjectResponse(
      final RoutingContext ctx,
      final HttpServerResponse response,
      final JsonRpcResponse jsonRpcResponse)
      throws IOException {
    response.setStatusCode(status(jsonRpcResponse).code());
    if (jsonRpcResponse.getType() == JsonRpcResponseType.NONE) {
      response.end();
      return;
    }

    try (final JsonResponseStreamer streamer =
        new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
      // underlying output stream lifecycle is managed by the json object writer
      lazyTraceLogger(() -> JSON_OBJECT_MAPPER.writeValueAsString(jsonRpcResponse));
      JSON_OBJECT_WRITER.writeValue(streamer, jsonRpcResponse);
    }
  }

  private void processRequestBodyAsArray(
      final RoutingContext ctx,
      final HttpServerResponse response,
      final Optional<User> user,
      final Context spanContext)
      throws IOException {
    final JsonArray jsonArray = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());
    lazyTraceLogger(jsonArray::toString);

    vertx
        .eventBus()
        .request(
            RPC_EXECUTE_ARRAY.getAddress(),
            new JsonRpcExecutorArrayRequest(
                user, spanContext, () -> !ctx.response().closed(), jsonArray))
        .onSuccess(
            msg -> {
              try {
                handleRpcExecutorArrayResponse(ctx, response, (JsonRpcResponse[]) msg.body());
              } catch (IOException e) {
                LOG.error(
                    "Error while processing {}: {}", getRequestMethodName(ctx), e.getMessage());
                handleJsonRpcError(ctx, getRequestId(ctx), JsonRpcError.INTERNAL_ERROR);
              }
            })
        .onFailure(
            e -> {
              LOG.error("Error while processing {}: {}", getRequestMethodName(ctx), e.getMessage());
              handleJsonRpcError(
                  ctx,
                  getRequestId(ctx),
                  JsonRpcError.fromCode(((ReplyException) e).failureCode()));
            });
  }

  private void handleRpcExecutorArrayResponse(
      final RoutingContext ctx,
      final HttpServerResponse response,
      final JsonRpcResponse[] jsonResponseArray)
      throws IOException {
    final JsonRpcResponse[] completed =
        Arrays.stream(jsonResponseArray)
            .filter(jsonRpcResponse -> jsonRpcResponse.getType() != JsonRpcResponseType.NONE)
            .toArray(JsonRpcResponse[]::new);
    try (final JsonResponseStreamer streamer =
        new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
      // underlying output stream lifecycle is managed by the json object writer
      lazyTraceLogger(() -> JSON_OBJECT_MAPPER.writeValueAsString(completed));
      JSON_OBJECT_WRITER.writeValue(streamer, completed);
    }
  }

  private String getRequestMethodName(final RoutingContext ctx) {
    return getRequestField(ctx, "method");
  }

  private String getRequestId(final RoutingContext ctx) {
    return getRequestField(ctx, "id");
  }

  private String getRequestField(final RoutingContext ctx, final String fieldName) {
    if (!ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name())) {
      return "";
    }

    final JsonObject jsonObject = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
    return jsonObject.getString(fieldName);
  }

  private void handleJsonRpcError(
      final RoutingContext routingContext, final Object id, final JsonRpcError error) {
    final HttpServerResponse response = routingContext.response();
    if (!response.closed()) {
      response
          .setStatusCode(statusCodeFromError(error).code())
          .end(Json.encode(new JsonRpcErrorResponse(id, error)));
    }
  }

  private HttpResponseStatus status(final JsonRpcResponse response) {
    switch (response.getType()) {
      case UNAUTHORIZED:
        return HttpResponseStatus.UNAUTHORIZED;
      case ERROR:
        return statusCodeFromError(((JsonRpcErrorResponse) response).getError());
      case SUCCESS:
      case NONE:
      default:
        return HttpResponseStatus.OK;
    }
  }

  private HttpResponseStatus statusCodeFromError(final JsonRpcError error) {
    switch (error) {
      case INVALID_REQUEST:
      case PARSE_ERROR:
        return HttpResponseStatus.BAD_REQUEST;
      default:
        return HttpResponseStatus.OK;
    }
  }

  @FunctionalInterface
  private interface ExceptionThrowingSupplier<T> {
    T get() throws Exception;
  }

  private void lazyTraceLogger(final ExceptionThrowingSupplier<String> logMessageSupplier) {
    if (LOG.isTraceEnabled()) {
      try {
        LOG.trace(logMessageSupplier.get());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
