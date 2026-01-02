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

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonResponseStreamer;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class JsonRpcObjectExecutor extends AbstractJsonRpcExecutor {
  private final ObjectWriter jsonObjectWriter = createObjectWriter();

  public JsonRpcObjectExecutor(
      final JsonRpcExecutor jsonRpcExecutor,
      final Tracer tracer,
      final RoutingContext ctx,
      final JsonRpcConfiguration jsonRpcConfiguration) {
    super(jsonRpcExecutor, tracer, ctx, jsonRpcConfiguration);
  }

  @Override
  void execute() throws IOException {
    HttpServerResponse response = ctx.response();
    response = response.putHeader("Content-Type", APPLICATION_JSON);

    final JsonObject jsonRequest = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
    lazyTraceLogger(jsonRequest::toString);
    final JsonRpcResponse jsonRpcResponse =
        executeRequest(jsonRpcExecutor, tracer, jsonRequest, ctx);
    handleJsonObjectResponse(response, jsonRpcResponse, ctx);
  }

  @Override
  String getRpcMethodName(final RoutingContext ctx) {
    final JsonObject jsonObject = ctx.get(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name());
    return jsonObject.getString("method");
  }

  private void handleJsonObjectResponse(
      final HttpServerResponse response,
      final JsonRpcResponse jsonRpcResponse,
      final RoutingContext ctx)
      throws IOException {
    response.setStatusCode(status(jsonRpcResponse).code());
    if (jsonRpcResponse.getType() == RpcResponseType.NONE) {
      response.end();
    } else {
      try (final JsonResponseStreamer streamer =
          new JsonResponseStreamer(response, ctx.request().remoteAddress())) {
        // underlying output stream lifecycle is managed by the json object writer
        lazyTraceLogger(() -> getJsonObjectMapper().writeValueAsString(jsonRpcResponse));
        jsonObjectWriter.writeValue(streamer, jsonRpcResponse);
      }
    }
  }

  private static HttpResponseStatus status(final JsonRpcResponse response) {
    return switch (response.getType()) {
      case UNAUTHORIZED -> HttpResponseStatus.UNAUTHORIZED;
      case ERROR -> statusCodeFromError(((JsonRpcErrorResponse) response).getErrorType());
      default -> HttpResponseStatus.OK;
    };
  }

  private ObjectWriter createObjectWriter() {
    ObjectWriter writer =
        jsonRpcConfiguration.isPrettyJsonEnabled()
            ? getJsonObjectMapper().writerWithDefaultPrettyPrinter()
            : getJsonObjectMapper().writer();
    return writer
        .without(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM)
        .with(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  private static HttpResponseStatus statusCodeFromError(final RpcErrorType error) {
    return switch (error) {
      case INVALID_REQUEST, PARSE_ERROR -> HttpResponseStatus.BAD_REQUEST;
      default -> HttpResponseStatus.OK;
    };
  }
}
