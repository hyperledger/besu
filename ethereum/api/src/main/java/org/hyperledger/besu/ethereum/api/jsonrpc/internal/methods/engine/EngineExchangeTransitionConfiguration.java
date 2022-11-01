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

import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.ENGINE_EXCHANGE_TRANSITION_CONFIGURATION;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineExchangeTransitionConfigurationParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineExchangeTransitionConfigurationResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineExchangeTransitionConfiguration extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG =
      LoggerFactory.getLogger(EngineExchangeTransitionConfiguration.class);

  // use (2^256 - 2^10) if engine is enabled in the absence of a TTD configuration
  static final Difficulty FALLBACK_TTD_DEFAULT =
      Difficulty.fromHexString(
          "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffc00");

  private static final Supplier<ObjectMapper> mapperSupplier = Suppliers.memoize(ObjectMapper::new);

  public EngineExchangeTransitionConfiguration(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolContext, engineCallListener);
  }

  @Override
  public String getName() {
    return ENGINE_EXCHANGE_TRANSITION_CONFIGURATION.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    engineCallListener.executionEngineCalled();

    final EngineExchangeTransitionConfigurationParameter remoteTransitionConfiguration =
        requestContext.getRequiredParameter(
            0, EngineExchangeTransitionConfigurationParameter.class);
    final Object reqId = requestContext.getRequest().getId();

    traceLambda(
        LOG,
        "received transitionConfiguration: {}",
        () -> {
          try {
            return mapperSupplier.get().writeValueAsString(remoteTransitionConfiguration);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        });

    final Optional<BlockHeader> maybeTerminalPoWBlockHeader =
        mergeContextOptional.flatMap(MergeContext::getTerminalPoWBlock);

    final EngineExchangeTransitionConfigurationResult localTransitionConfiguration =
        new EngineExchangeTransitionConfigurationResult(
            mergeContextOptional
                .map(c -> c.getTerminalTotalDifficulty())
                .orElse(FALLBACK_TTD_DEFAULT),
            maybeTerminalPoWBlockHeader.map(BlockHeader::getHash).orElse(Hash.ZERO),
            maybeTerminalPoWBlockHeader.map(BlockHeader::getNumber).orElse(0L));

    if (!localTransitionConfiguration
        .getTerminalTotalDifficulty()
        .equals(remoteTransitionConfiguration.getTerminalTotalDifficulty())) {
      LOG.debug(
          "Configured terminal total difficulty {} does not match value of consensus client {}",
          localTransitionConfiguration.getTerminalTotalDifficulty(),
          remoteTransitionConfiguration.getTerminalTotalDifficulty());
    }

    if (!localTransitionConfiguration
        .getTerminalBlockHash()
        .equals(remoteTransitionConfiguration.getTerminalBlockHash())) {
      LOG.debug(
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
}
