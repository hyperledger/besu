/*
 * Copyright contributors to Besu.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineJsonRpcParserHandler {

  private static final Logger LOG = LoggerFactory.getLogger(EngineJsonRpcParserHandler.class);

  private final ObjectMapper engineMapper;

  public EngineJsonRpcParserHandler() {
    this.engineMapper = new ObjectMapper();
    // Configure with max string length for large payloads
    StreamReadConstraints src =
        StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build();
    engineMapper.getFactory().setStreamReadConstraints(src);
  }

  public Handler<RoutingContext> handler() {
    return ctx -> {
      final HttpServerResponse response = ctx.response();
      if (ctx.getBody() == null) {
        errorResponse(response, RpcErrorType.PARSE_ERROR);
      } else {
        try {
          // Parse the JSON using our custom ObjectMapper
          String bodyString = ctx.getBodyAsString();
          JsonNode jsonNode = engineMapper.readTree(bodyString);

          if (jsonNode.isObject()) {
            // Convert Jackson JsonNode to Vert.x JsonObject
            JsonObject jsonObject = new JsonObject(engineMapper.writeValueAsString(jsonNode));
            ctx.put(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name(), jsonObject);
          } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            if (arrayNode.isEmpty()) {
              errorResponse(response, RpcErrorType.INVALID_REQUEST);
              return;
            }
            // Convert Jackson JsonNode to Vert.x JsonArray
            JsonArray jsonArray = new JsonArray(engineMapper.writeValueAsString(jsonNode));
            ctx.put(ContextKey.REQUEST_BODY_AS_JSON_ARRAY.name(), jsonArray);
          } else {
            errorResponse(response, RpcErrorType.PARSE_ERROR);
            return;
          }
        } catch (JsonProcessingException e) {
          LOG.atDebug()
              .setMessage("Error parsing JSON with Engine mapper: {}")
              .addArgument(e.getMessage())
              .log();
          errorResponse(response, RpcErrorType.PARSE_ERROR);
          return;
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
