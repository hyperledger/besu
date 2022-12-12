/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.execution;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcExecutorVerticle extends AbstractVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcExecutorVerticle.class);

  private final JsonRpcExecutor jsonRpcExecutor;
  private final Tracer tracer;

  public JsonRpcExecutorVerticle(final JsonRpcExecutor jsonRpcExecutor, final Tracer tracer) {
    this.jsonRpcExecutor = jsonRpcExecutor;
    this.tracer = tracer;
  }

  @Override
  public void start() {
    vertx.eventBus().consumer("ethereum.api.json.rpc.executor", this::handleJsonRpcExecutor);
  }

  private void handleJsonRpcExecutor(final Message<JsonRpcExecutorRequest> executorRequestMessage) {
    final JsonRpcExecutorRequest request = executorRequestMessage.body();
    LOG.info("Received executorRequest {}", request);

    final JsonRpcResponse jsonRpcResponse =
        jsonRpcExecutor.execute(
            request.getOptionalUser(),
            tracer,
            request.getSpanContext(),
            request.getAlive(),
            request.getJsonRpcRequest(),
            req -> req.mapTo(JsonRpcRequest.class));

    executorRequestMessage.reply(jsonRpcResponse);
  }
}
