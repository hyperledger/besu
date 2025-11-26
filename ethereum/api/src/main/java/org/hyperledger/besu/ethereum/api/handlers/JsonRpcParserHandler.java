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

import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class JsonRpcParserHandler {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(JSON_FACTORY);

  private JsonRpcParserHandler() {}

  public static Handler<Buffer> ipcHandler(
      final BiConsumer<JsonObject, JsonArray> onParsed, final Runnable onError) {

    return buffer -> {
      if (buffer.length() == 0) {
        onError.run();
        return;
      }

      try (ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer.getBytes());
          JsonParser parser = JSON_FACTORY.createParser(inputStream)) {

        // Read multiple JSON objects from the stream, similar to Go's json.Decoder
        while (parser.nextToken() != null) {
          JsonToken token = parser.currentToken();

          if (token == JsonToken.START_OBJECT) {
            // Parse JSON object
            JsonObject obj = new JsonObject(OBJECT_MAPPER.readTree(parser).toString());
            onParsed.accept(obj, null);
          } else if (token == JsonToken.START_ARRAY) {
            // Parse JSON array
            JsonArray arr = new JsonArray(OBJECT_MAPPER.readTree(parser).toString());
            onParsed.accept(null, arr);
          }
        }
      } catch (IOException e) {
        onError.run();
      }
    };
  }

  public static Handler<RoutingContext> handler() {
    return ctx -> {
      final HttpServerResponse response = ctx.response();
      if (ctx.getBody() == null) {
        errorResponse(response, RpcErrorType.PARSE_ERROR);
      } else {
        try {
          ctx.put(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name(), ctx.getBodyAsJson());
        } catch (DecodeException | ClassCastException jsonObjectDecodeException) {
          try {
            final JsonArray batchRequest = ctx.getBodyAsJsonArray();
            if (batchRequest.isEmpty()) {
              errorResponse(response, RpcErrorType.INVALID_REQUEST);
              return;
            } else {
              ctx.put(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name(), batchRequest);
            }
          } catch (DecodeException | ClassCastException jsonArrayDecodeException) {
            errorResponse(response, RpcErrorType.PARSE_ERROR);
            return;
          }
        }
        ctx.next();
      }
    };
  }

  private static void errorResponse(
      final HttpServerResponse response, final RpcErrorType rpcError) {
    if (!response.closed()) {
      response
          .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
          .end(Json.encode(new JsonRpcErrorResponse(null, rpcError)));
    }
  }
}
