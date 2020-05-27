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
package org.hyperledger.besu.ethereum.api.jsonrpc.timeout;

import org.hyperledger.besu.ethereum.api.jsonrpc.context.ContextKey;

import java.util.Map;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class EthRpcTimeoutHandler {

  public static Handler<RoutingContext> handler(
      final Map<String, TimeoutOptions> timeoutOptionsByMethod) {
    assert timeoutOptionsByMethod != null;
    return ctx -> {
      try {
        final String bodyAsString = ctx.getBodyAsString();
        if (bodyAsString != null) {
          final String json = ctx.getBodyAsString().trim();
          if (!json.isEmpty() && json.charAt(0) == '{') {
            final JsonObject requestBodyJsonObject = new JsonObject(json);
            ctx.put(ContextKey.REQUEST_BODY_AS_JSON_OBJECT.name(), requestBodyJsonObject);
            final String method = requestBodyJsonObject.getString("method");
            if (timeoutOptionsByMethod.containsKey(method)) {
              final TimeoutOptions options = timeoutOptionsByMethod.get(method);
              long tid =
                  ctx.vertx().setTimer(options.getTimeout(), t -> ctx.fail(options.getErrorCode()));
              ctx.addBodyEndHandler(v -> ctx.vertx().cancelTimer(tid));
            }
          }
        }
      } finally {
        ctx.next();
      }
    };
  }
}
