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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.ENGINE_EXCHANGE_CAPABILITIES;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.ENGINE_PREPARE_PAYLOAD_DEBUG;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineExchangeCapabilities extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(EngineExchangeCapabilities.class);

  public EngineExchangeCapabilities(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolContext, engineCallListener);
  }

  @Override
  public String getName() {
    return ENGINE_EXCHANGE_CAPABILITIES.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    engineCallListener.executionEngineCalled();

    final Object reqId = requestContext.getRequest().getId();

    LOG.atTrace()
        .setMessage("received remote capabilities: {}")
        .addArgument(() -> requestContext.getRequiredParameter(0, String[].class))
        .log();

    final List<String> localCapabilities =
        Stream.of(RpcMethod.values())
            .filter(e -> e.getMethodName().startsWith("engine_"))
            .filter(e -> !e.equals(ENGINE_EXCHANGE_CAPABILITIES))
            .filter(e -> !e.equals(ENGINE_PREPARE_PAYLOAD_DEBUG))
            .filter(e -> !e.getMethodName().endsWith("6110"))
            .map(RpcMethod::getMethodName)
            .collect(Collectors.toList());

    return respondWith(reqId, localCapabilities);
  }

  private JsonRpcResponse respondWith(
      final Object requestId, final List<String> localCapabilities) {
    return new JsonRpcSuccessResponse(requestId, localCapabilities);
  }
}
