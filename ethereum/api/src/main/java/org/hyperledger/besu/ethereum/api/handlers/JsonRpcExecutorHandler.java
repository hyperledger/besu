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
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcExecutorHandler {

  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcExecutorHandler.class);
  private static final String SPAN_CONTEXT = "span_context";
  private static final String APPLICATION_JSON = "application/json";
  private static ObjectMapper jsonObjectMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module()); // Handle JDK8 Optionals (de)serialization
  private static ObjectWriter jsonObjectWriter = createObjectWriter(jsonObjectMapper);

  private JsonRpcExecutorHandler() {}

  public static Handler<RoutingContext> handler(
      final ObjectMapper jsonObjectMapper,
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    JsonRpcExecutorHandler.jsonObjectMapper = jsonObjectMapper;
    JsonRpcExecutorHandler.jsonObjectWriter = createObjectWriter(jsonObjectMapper);
    return handler(jsonRpcExecutor, tracer, jsonRpcConfiguration);
  }

  public static Handler<RoutingContext> handler(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    return ctx -> {
      HttpServerResponse response = ctx.response();
      try {
        response = response.putHeader("Content-Type", APPLICATION_JSON);
        if (isJsonObjectRequest(ctx)) {
          final JsonRpcResponse jsonRpcResponse =
              executeJsonObjectRequest(jsonRpcExecutor, tracer, ctx);
          handleJsonObjectResponse(response, jsonRpcResponse, ctx);
        } else if (isJsonArrayRequest(ctx)) {
          final List<JsonRpcResponse> jsonRpcBatchResponses;
          try {
            jsonRpcBatchResponses =
                executeJsonArrayRequest(jsonRpcExecutor, tracer, ctx, jsonRpcConfiguration);
            handleJsonArrayResponse(response, jsonRpcBatchResponses, ctx);
          } catch (final InvalidParameterException e) {
            handleJsonRpcError(ctx, null, JsonRpcError.EXCEEDS_RPC_MAX_BATCH_SIZE);
          } catch (final RuntimeException e) {
            response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
          }
        } else {
          handleJsonRpcError(ctx, null, JsonRpcError.PARSE_ERROR);
        }
      } catch (final IOException ex) {
        final String method = getRpcMethodName(ctx);
        LOG.error("{} - Error streaming JSON-RPC response", method, ex);
      } catch (final RuntimeException e) {
        handleJsonRpcError(ctx, null, JsonRpcError.INTERNAL_ERROR);
      }
    };
  }

  private static ObjectWriter createObjectWriter(final ObjectMapper jsonObjectMapper) {
    return jsonObjectMapper
        .writerWithDefaultPrettyPrinter()
        .without(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM)
        .with(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  private static boolean isJsonObjectRequest(final RoutingContext ctx) {
    return ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
  }

  private static boolean isJsonArrayRequest(final RoutingContext ctx) {
    return ctx.data().containsKey(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());
  }

  private static JsonRpcResponse executeRequest(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final JsonObject jsonRequest,
      final RoutingContext ctx) {
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

  private static JsonRpcResponse executeJsonObjectRequest(
      final JsonRpcExecutor jsonRpcExecutor, final Tracer tracer, final RoutingContext ctx) {
    final JsonObject jsonRequest = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
    lazyTraceLogger(jsonRequest::toString);
    return executeRequest(jsonRpcExecutor, tracer, jsonRequest, ctx);
  }

  private static List<JsonRpcResponse> executeJsonArrayRequest(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final RoutingContext ctx,
      final JsonRpcConfiguration jsonRpcConfiguration)
      throws InvalidParameterException {
    final JsonArray batchJsonRequest = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name());
    lazyTraceLogger(batchJsonRequest::toString);
    final List<JsonRpcResponse> jsonRpcBatchResponses = new ArrayList<>();

    if (jsonRpcConfiguration.getMaxBatchSize() > 0
        && batchJsonRequest.size() > jsonRpcConfiguration.getMaxBatchSize()) {
      throw new InvalidParameterException();
    }

    for (int i = 0; i < batchJsonRequest.size(); i++) {
      final JsonObject jsonRequest;
      try {
        jsonRequest = batchJsonRequest.getJsonObject(i);
      } catch (final ClassCastException e) {
        jsonRpcBatchResponses.add(new JsonRpcErrorResponse(null, INVALID_REQUEST));
        continue;
      }
      jsonRpcBatchResponses.add(executeRequest(jsonRpcExecutor, tracer, jsonRequest, ctx));
    }
    return jsonRpcBatchResponses;
  }

  private static void handleJsonObjectResponse(
      final HttpServerResponse response,
      final JsonRpcResponse jsonRpcResponse,
      final RoutingContext ctx)
      throws IOException {
    response.setStatusCode(status(jsonRpcResponse).code());
    if (jsonRpcResponse.getType() == JsonRpcResponseType.NONE) {
      response.end();
    } else {
      try (final JsonResponseStreamer streamer =
          new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
        // underlying output stream lifecycle is managed by the json object writer
        lazyTraceLogger(() -> jsonObjectMapper.writeValueAsString(jsonRpcResponse));
        jsonObjectWriter.writeValue(streamer, jsonRpcResponse);
      }
    }
  }

  private static void handleJsonArrayResponse(
      final HttpServerResponse response,
      final List<JsonRpcResponse> jsonRpcBatchResponses,
      final RoutingContext ctx)
      throws IOException {
    final JsonRpcResponse[] completed =
        jsonRpcBatchResponses.stream()
            .filter(jsonRpcResponse -> jsonRpcResponse.getType() != JsonRpcResponseType.NONE)
            .toArray(JsonRpcResponse[]::new);
    try (final JsonResponseStreamer streamer =
        new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
      // underlying output stream lifecycle is managed by the json object writer
      lazyTraceLogger(() -> jsonObjectMapper.writeValueAsString(completed));
      jsonObjectWriter.writeValue(streamer, completed);
    }
  }

  private static String getRpcMethodName(final RoutingContext ctx) {
    if (isJsonObjectRequest(ctx)) {
      final JsonObject jsonObject = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
      return jsonObject.getString("method");
    } else {
      return "";
    }
  }

  private static void handleJsonRpcError(
      final RoutingContext routingContext, final Object id, final JsonRpcError error) {
    final HttpServerResponse response = routingContext.response();
    if (!response.closed()) {
      response
          .setStatusCode(statusCodeFromError(error).code())
          .end(Json.encode(new JsonRpcErrorResponse(id, error)));
    }
  }

  private static HttpResponseStatus status(final JsonRpcResponse response) {
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

  private static HttpResponseStatus statusCodeFromError(final JsonRpcError error) {
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

  private static void lazyTraceLogger(final ExceptionThrowingSupplier<String> logMessageSupplier) {
    if (LOG.isTraceEnabled()) {
      try {
        LOG.trace(logMessageSupplier.get());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
