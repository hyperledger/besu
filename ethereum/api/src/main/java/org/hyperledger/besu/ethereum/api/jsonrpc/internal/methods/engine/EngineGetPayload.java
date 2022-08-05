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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetPayload extends ExecutionEngineJsonRpcMethod {

  private final BlockResultFactory blockResultFactory;
  private static final Logger LOG = LoggerFactory.getLogger(EngineGetPayload.class);

  public EngineGetPayload(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final BlockResultFactory blockResultFactory) {
    super(vertx, protocolContext);
    this.blockResultFactory = blockResultFactory;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {
    final PayloadIdentifier payloadId = request.getRequiredParameter(0, PayloadIdentifier.class);

    final Optional<Block> block = mergeContext.get().retrieveBlockById(payloadId);
    if (block.isPresent()) {
      var proposal = block.get();
      var proposalHeader = proposal.getHeader();
      infoLambda(
          LOG,
          "Fetch block proposal by identifier: {}, hash: {}, number: {}, coinbase: {}, transaction count: {}",
          () -> payloadId.toHexString(),
          () -> proposalHeader.getHash(),
          () -> proposalHeader.getNumber(),
          () -> proposalHeader.getCoinbase(),
          () -> proposal.getBody().getTransactions().size());
      debugLambda(LOG, "assembledBlock {}", () -> block.map(Block::toString).get());
      return new JsonRpcSuccessResponse(
          request.getRequest().getId(),
          blockResultFactory.enginePayloadTransactionComplete(block.get()));
    }
    return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.UNKNOWN_PAYLOAD);
  }
}
