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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.RewardTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class TraceBlock extends AbstractBlockParameterMethod {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Supplier<BlockTracer> blockTracerSupplier;
  private final ProtocolSchedule<?> protocolSchedule;

  public TraceBlock(
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule<?> protocolSchedule,
      final BlockchainQueries queries) {
    super(queries);
    this.blockTracerSupplier = blockTracerSupplier;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_BLOCK.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    if (blockNumber == BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Nothing to trace for the genesis block
      return emptyResult();
    }

    return getBlockchainQueries()
        .getBlockchain()
        .getBlockByNumber(blockNumber)
        .map(this::traceBlock)
        .orElse(null);
  }

  private Object traceBlock(final Block block) {

    if (block == null) {
      return emptyResult();
    }
    final ArrayNode resultArrayNode = MAPPER.createArrayNode();

    blockTracerSupplier
        .get()
        .trace(block, new DebugOperationTracer(TraceOptions.DEFAULT))
        .ifPresent(
            blockTrace ->
                generateTracesFromTransactionTraceAndBlock(
                    blockTrace.getTransactionTraces(), block, resultArrayNode));

    generateRewardsFromBlock(block, resultArrayNode);

    return resultArrayNode;
  }

  private void generateTracesFromTransactionTraceAndBlock(
      final List<TransactionTrace> transactionTraces,
      final Block block,
      final ArrayNode resultArrayNode) {

    transactionTraces.forEach(
        transactionTrace ->
            FlatTraceGenerator.generateFromTransactionTraceAndBlock(
                    protocolSchedule, transactionTrace, block)
                .forEachOrdered(resultArrayNode::addPOJO));
  }

  private void generateRewardsFromBlock(final Block block, final ArrayNode resultArrayNode) {
    RewardTraceGenerator.generateFromBlock(protocolSchedule, block)
        .forEachOrdered(resultArrayNode::addPOJO);
  }

  private Object emptyResult() {
    return MAPPER.createArrayNode();
  }
}
