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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Collection;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugTraceBlock implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(DebugTraceBlock.class);
  private final Supplier<BlockTracer> blockTracerSupplier;
  private final BlockHeaderFunctions blockHeaderFunctions;
  private final BlockchainQueries blockchain;

  public DebugTraceBlock(
      final Supplier<BlockTracer> blockTracerSupplier,
      final BlockHeaderFunctions blockHeaderFunctions,
      final BlockchainQueries blockchain) {
    this.blockTracerSupplier = blockTracerSupplier;
    this.blockHeaderFunctions = blockHeaderFunctions;
    this.blockchain = blockchain;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_BLOCK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final String input = requestContext.getRequiredParameter(0, String.class);
    final Block block;
    try {
      block = Block.readFrom(RLP.input(Bytes.fromHexString(input)), this.blockHeaderFunctions);
    } catch (final RLPException e) {
      LOG.debug("Failed to parse block RLP", e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
    final TraceOptions traceOptions =
        requestContext
            .getOptionalParameter(1, TransactionTraceParams.class)
            .map(TransactionTraceParams::traceOptions)
            .orElse(TraceOptions.DEFAULT);

    if (this.blockchain.blockByHash(block.getHeader().getParentHash()).isPresent()) {
      final Collection<DebugTraceTransactionResult> results =
          blockTracerSupplier
              .get()
              .trace(block, new DebugOperationTracer(traceOptions))
              .map(BlockTrace::getTransactionTraces)
              .map(DebugTraceTransactionResult::of)
              .orElse(null);
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), results);
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.PARENT_BLOCK_NOT_FOUND);
    }
  }
}
