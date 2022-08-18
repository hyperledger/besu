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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.TIMEOUT_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.RewardTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceBlock extends AbstractBlockParameterMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TraceBlock.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final Supplier<BlockTracer> blockTracerSupplier;
  private final int traceTimeout = 5000;
  protected final ProtocolSchedule protocolSchedule;

  public TraceBlock(
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule,
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
      final JsonRpcRequestContext requestContext, final long blockNumber) {
    if (blockNumber == BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Nothing to trace for the genesis block
      return emptyResult().getArrayNode();
    }
    LOG.trace("Received RPC rpcName={} block={}", getName(), blockNumber);

    final ExecutorService executor = Executors.newSingleThreadExecutor();

    final Callable<Object> traceTask =
        () ->
            getBlockchainQueries()
                .getBlockchain()
                .getBlockByNumber(blockNumber)
                .map(block -> traceBlock(block, Optional.empty()))
                .map(ArrayNodeWrapper::getArrayNode)
                .orElse(null);

    final Future<Object> result = executor.submit(traceTask);
    try {
      return result.get(traceTimeout, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException ex) {
      LOG.debug(
          "trace_block not completed within {}ms. Specified block number: {}",
          traceTimeout,
          blockNumber);
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), TIMEOUT_ERROR);
    } catch (final Exception ex) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    } finally {
      executor.shutdown();
    }
  }

  protected ArrayNodeWrapper traceBlock(
      final Block block, final Optional<FilterParameter> filterParameter) {

    if (block == null) {
      return emptyResult();
    }
    final ArrayNodeWrapper resultArrayNode = new ArrayNodeWrapper(MAPPER.createArrayNode());

    blockTracerSupplier
        .get()
        .trace(block, new DebugOperationTracer(new TraceOptions(false, false, true)))
        .ifPresent(
            blockTrace ->
                generateTracesFromTransactionTraceAndBlock(
                    filterParameter, blockTrace.getTransactionTraces(), block, resultArrayNode));

    generateRewardsFromBlock(filterParameter, block, resultArrayNode);

    return resultArrayNode;
  }

  protected void generateTracesFromTransactionTraceAndBlock(
      final Optional<FilterParameter> filterParameter,
      final List<TransactionTrace> transactionTraces,
      final Block block,
      final ArrayNodeWrapper resultArrayNode) {
    transactionTraces.forEach(
        transactionTrace ->
            FlatTraceGenerator.generateFromTransactionTraceAndBlock(
                    protocolSchedule, transactionTrace, block)
                .forEachOrdered(resultArrayNode::addPOJO));
  }

  protected void generateRewardsFromBlock(
      final Optional<FilterParameter> maybeFilterParameter,
      final Block block,
      final ArrayNodeWrapper resultArrayNode) {
    RewardTraceGenerator.generateFromBlock(protocolSchedule, block)
        .forEachOrdered(resultArrayNode::addPOJO);
  }

  private ArrayNodeWrapper emptyResult() {
    return new ArrayNodeWrapper(MAPPER.createArrayNode());
  }
}
