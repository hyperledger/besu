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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_REQUEST;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonResponseStreamer;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutorBatchRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutorRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcNoResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcUnauthorizedResponse;
import org.hyperledger.besu.util.vertx.GenericMessageCodec;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentelemetry.context.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
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

  private final Vertx vertx;

  public NonBlockingJsonRpcExecutorHandler(final Vertx vertx) {
    this.vertx = vertx;

    vertx
        .eventBus()
        .registerDefaultCodec(
            JsonRpcExecutorRequest.class,
            new GenericMessageCodec<>(JsonRpcExecutorRequest.class, LOG))
        .registerDefaultCodec(
            JsonRpcExecutorBatchRequest.class,
            new GenericMessageCodec<>(JsonRpcExecutorBatchRequest.class, LOG));
    vertx
        .eventBus()
        .registerDefaultCodec(
            JsonRpcSuccessResponse.class,
            new GenericMessageCodec<>(JsonRpcSuccessResponse.class, LOG))
        .registerDefaultCodec(
            JsonRpcErrorResponse.class, new GenericMessageCodec<>(JsonRpcErrorResponse.class, LOG))
        .registerDefaultCodec(
            JsonRpcNoResponse.class, new GenericMessageCodec<>(JsonRpcNoResponse.class, LOG))
        .registerDefaultCodec(
            JsonRpcUnauthorizedResponse.class,
            new GenericMessageCodec<>(JsonRpcUnauthorizedResponse.class, LOG));
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

      if (isRequestBodyAnObject) {
        processRequestBodyAsObject(ctx, response, user, spanContext);
        //        return;
      }

      //      processRequestBodyAsArray(ctx, response, user, spanContext);
    } catch (final IOException ex) {
      final String method = getRpcMethodName(ctx);
      LOG.error("{} - Error streaming JSON-RPC response", method, ex);
    } catch (final RuntimeException e) {
      LOG.error("RPC call failed: {}", e.getMessage());
      handleJsonRpcError(ctx, null, JsonRpcError.INTERNAL_ERROR);
    }
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
            "ethereum.api.json.rpc.executor",
            new JsonRpcExecutorRequest(
                user, spanContext, () -> !ctx.response().closed(), jsonRequest))
        .onSuccess(
            msg -> {
              try {
                handleRequestBodyAsObjectResult(ctx, response, (JsonRpcResponse) msg.body());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .onFailure(
            e -> {
              LOG.error(
                  "Error while processing {}: {}", jsonRequest.getString("method"), e.getMessage());
              throw new RuntimeException(e);
            });
  }

  private void handleRequestBodyAsObjectResult(
      final RoutingContext ctx,
      final HttpServerResponse response,
      final JsonRpcResponse jsonRpcResponse)
      throws IOException {
    response.setStatusCode(status(jsonRpcResponse).code());
    if (jsonRpcResponse.getType() == JsonRpcResponseType.NONE) {
      response.end();
    } else {
      try (final JsonResponseStreamer streamer =
          new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
        // underlying output stream lifecycle is managed by the json object writer
        lazyTraceLogger(() -> JSON_OBJECT_MAPPER.writeValueAsString(jsonRpcResponse));
        JSON_OBJECT_WRITER.writeValue(streamer, jsonRpcResponse);
      }
    }
  }

  //    private void processRequestBodyAsArray(
  //        final RoutingContext ctx,
  //        final HttpServerResponse response,
  //        final Optional<User> user,
  //        final Context spanContext)
  //        throws IOException {
  //      final JsonArray batchJsonRequest = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());
  //      lazyTraceLogger(batchJsonRequest::toString);
  //      List<JsonRpcResponse> jsonRpcBatchResponses = new ArrayList<>(batchJsonRequest.size());
  //      try {
  //        for (int i = 0; i < batchJsonRequest.size(); i++) {
  //          final JsonObject jsonRequest;
  //          try {
  //            jsonRequest = batchJsonRequest.getJsonObject(i);
  //          } catch (ClassCastException e) {
  //            jsonRpcBatchResponses.add(new JsonRpcErrorResponse(null, INVALID_REQUEST));
  //            continue;
  //          }
  //          vertx
  //              .eventBus()
  //              .request(
  //                  "ethereum.api.json.rpc.executor",
  //                  new JsonRpcExecutorBatchRequest(
  //                      i, user, spanContext, () -> !ctx.response().closed(), jsonRequest))
  //              .onSuccess(
  //                  msg -> handleRequestBodyAsArrayResult(jsonRpcBatchResponses, (JsonRpcResponse)
  // msg.body())
  //              )
  //              .onFailure(e -> {
  //                LOG.error(
  //                    "Error while processing {}: {}", jsonRequest.getString("method"),
  // e.getMessage());
  //                throw new RuntimeException(e);
  //              });
  //
  //        }
  //      } catch (RuntimeException e) {
  //        response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
  //        return;
  //      }
  //      final JsonRpcResponse[] completed =
  //          jsonRpcBatchResponses.stream()
  //              .filter(jsonRpcResponse -> jsonRpcResponse.getType() != JsonRpcResponseType.NONE)
  //              .toArray(JsonRpcResponse[]::new);
  //      try (final JsonResponseStreamer streamer =
  //               new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
  //        // underlying output stream lifecycle is managed by the json object writer
  //        lazyTraceLogger(() -> JSON_OBJECT_MAPPER.writeValueAsString(completed));
  //        JSON_OBJECT_WRITER.writeValue(streamer, completed);
  //      }
  //    }
  //
  //  private void handleRequestBodyAsArrayResult(final List<JsonRpcResponse> jsonRpcBatchResponses,
  // final JsonRpcResponse jsonRpcResponse) {
  //  }

  private String getRpcMethodName(final RoutingContext ctx) {
    if (ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name())) {
      final JsonObject jsonObject = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
      return jsonObject.getString("method");
    } else {
      return "";
    }
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
      case INVALID_PARAMS:
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
