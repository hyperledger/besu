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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineExchangeTransitionConfigurationParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineExchangeTransitionConfigurationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineExchangeTransitionConfiguration extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG =
      LoggerFactory.getLogger(EngineExchangeTransitionConfiguration.class);

  public EngineExchangeTransitionConfiguration(
      final Vertx vertx, final ProtocolContext protocolContext) {
    super(vertx, protocolContext);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final EngineExchangeTransitionConfigurationParameter remoteTransitionConfiguration =
        requestContext.getRequiredParameter(
            0, EngineExchangeTransitionConfigurationParameter.class);
    final Object reqId = requestContext.getRequest().getId();

    traceLambda(
        LOG,
        "received transitionConfiguration: {}",
        () -> Json.encodePrettily(remoteTransitionConfiguration));

    if (remoteTransitionConfiguration.getTerminalBlockNumber() != 0L) {
      return respondWithError(reqId, INVALID_PARAMS);
    }

    final Optional<BlockHeader> maybeTerminalPoWBlockHeader = mergeContext.getTerminalPoWBlock();

    final EngineExchangeTransitionConfigurationResult localTransitionConfiguration =
        new EngineExchangeTransitionConfigurationResult(
            mergeContext.getTerminalTotalDifficulty(),
            maybeTerminalPoWBlockHeader.map(BlockHeader::getHash).orElse(Hash.ZERO),
            maybeTerminalPoWBlockHeader.map(BlockHeader::getNumber).orElse(0L));

    if (!localTransitionConfiguration
        .getTerminalTotalDifficulty()
        .equals(remoteTransitionConfiguration.getTerminalTotalDifficulty())) {
      LOG.warn(
          "Configured terminal total difficulty {} does not match value of consensus client {}",
          localTransitionConfiguration.getTerminalTotalDifficulty(),
          remoteTransitionConfiguration.getTerminalTotalDifficulty());
    }

    if (!localTransitionConfiguration
        .getTerminalBlockHash()
        .equals(remoteTransitionConfiguration.getTerminalBlockHash())) {
      LOG.warn(
          "Configured terminal block hash {} does not match value of consensus client {}",
          localTransitionConfiguration.getTerminalBlockHash(),
          remoteTransitionConfiguration.getTerminalBlockHash());
    }

    if (localTransitionConfiguration.getTerminalBlockNumber()
        != remoteTransitionConfiguration.getTerminalBlockNumber()) {
      LOG.debug(
          "Configured terminal block number {} does not match value of consensus client {}",
          localTransitionConfiguration.getTerminalBlockNumber(),
          remoteTransitionConfiguration.getTerminalBlockNumber());
    }

    return respondWith(reqId, localTransitionConfiguration);
  }

  private JsonRpcResponse respondWith(
      final Object requestId,
      final EngineExchangeTransitionConfigurationResult transitionConfiguration) {
    return new JsonRpcSuccessResponse(requestId, transitionConfiguration);
  }

  private JsonRpcResponse respondWithError(
      final Object requestId, final JsonRpcError jsonRpcError) {
    return new JsonRpcErrorResponse(requestId, jsonRpcError);
  }
}
