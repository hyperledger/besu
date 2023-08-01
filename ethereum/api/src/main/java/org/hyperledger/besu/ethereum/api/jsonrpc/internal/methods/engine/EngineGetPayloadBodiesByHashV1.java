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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetPayloadBodiesResultV1;
import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetPayloadBodiesByHashV1 extends ExecutionEngineJsonRpcMethod {

  private static final int MAX_REQUEST_BLOCKS = 1024;
  private static final Logger LOG = LoggerFactory.getLogger(EngineGetPayloadBodiesByHashV1.class);
  private final BlockResultFactory blockResultFactory;

  public EngineGetPayloadBodiesByHashV1(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final BlockResultFactory blockResultFactory,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolContext, engineCallListener);
    this.blockResultFactory = blockResultFactory;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_PAYLOAD_BODIES_BY_HASH_V1.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {
    engineCallListener.executionEngineCalled();

    final Object reqId = request.getRequest().getId();

    final Hash[] blockHashes = request.getRequiredParameter(0, Hash[].class);

    LOG.atTrace()
        .setMessage("{} parameters: blockHashes {}")
        .addArgument(this::getName)
        .addArgument(blockHashes)
        .log();

    if (blockHashes.length > getMaxRequestBlocks()) {
      return new JsonRpcErrorResponse(reqId, RpcErrorType.INVALID_RANGE_REQUEST_TOO_LARGE);
    }

    final Blockchain blockchain = protocolContext.getBlockchain();

    final EngineGetPayloadBodiesResultV1 engineGetPayloadBodiesResultV1 =
        blockResultFactory.payloadBodiesCompleteV1(
            Arrays.stream(blockHashes).map(blockchain::getBlockBody).collect(Collectors.toList()));

    return new JsonRpcSuccessResponse(reqId, engineGetPayloadBodiesResultV1);
  }

  protected int getMaxRequestBlocks() {
    return MAX_REQUEST_BLOCKS;
  }
}
