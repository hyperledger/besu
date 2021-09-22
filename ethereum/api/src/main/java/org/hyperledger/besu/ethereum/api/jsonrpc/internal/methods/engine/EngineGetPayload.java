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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ConsensusAssembleBlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EngineGetPayload extends ExecutionEngineJsonRpcMethod {

  private final BlockResultFactory blockResultFactory;
  private final MiningCoordinator miningCoordinator;
  private final Blockchain blockchain;
  private static final Logger LOG = LogManager.getLogger();

  public EngineGetPayload(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final BlockResultFactory blockResultFactory,
      final MiningCoordinator miningCoordinator) {
    super(vertx, protocolContext);
    this.blockResultFactory = blockResultFactory;
    this.miningCoordinator = miningCoordinator;
    this.blockchain = protocolContext.getBlockchain();
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {

    // TODO: this is a naive repurposing of rayonism's consensus_assembleBlock to engine_getPayload.
    //      We should build a block on engine_preparePayload rather than here.
    //      https://github.com/ConsenSys/protocol-misc/issues/480

    final ConsensusAssembleBlockParameter blockParams =
        request.getRequiredParameter(0, ConsensusAssembleBlockParameter.class);

    final Optional<BlockHeader> parentBlockHeader =
        blockchain.getBlockHeader(blockParams.getParentHash());
    if (parentBlockHeader.isPresent()) {
      final Optional<Block> block =
          miningCoordinator.createBlock(parentBlockHeader.get(), blockParams.getTimestamp());
      if (block.isPresent()) {
        LOG.trace("assembledBlock " + block.map(b -> b.toString()).orElse(""));
        return new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            blockResultFactory.opaqueTransactionComplete(block.get()));
      }
    }
    return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.BLOCK_NOT_FOUND);
  }
}
