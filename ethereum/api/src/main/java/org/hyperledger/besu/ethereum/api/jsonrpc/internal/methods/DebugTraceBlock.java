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

import static org.hyperledger.besu.services.pipeline.PipelineBuilder.createPipelineFrom;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugTraceBlock implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(DebugTraceBlock.class);
  private final BlockHeaderFunctions blockHeaderFunctions;
  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public DebugTraceBlock(
      final ProtocolSchedule protocolSchedule,
      final BlockHeaderFunctions blockHeaderFunctions,
      final BlockchainQueries blockchainQueries) {
    this.protocolSchedule = protocolSchedule;
    this.blockHeaderFunctions = blockHeaderFunctions;
    this.blockchainQueries = blockchainQueries;
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
          requestContext.getRequest().getId(), RpcErrorType.INVALID_PARAMS);
    }
    final TraceOptions traceOptions =
        requestContext
            .getOptionalParameter(1, TransactionTraceParams.class)
            .map(TransactionTraceParams::traceOptions)
            .orElse(TraceOptions.DEFAULT);

    if (this.blockchainQueries.blockByHash(block.getHeader().getParentHash()).isPresent()) {
      final ArrayNode results = traceBlock(block, traceOptions).getArrayNode();
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), results);
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.PARENT_BLOCK_NOT_FOUND);
    }
  }

  protected ArrayNodeWrapper traceBlock(final Block block, final TraceOptions traceOptions) {

    if (block == null) {
      return emptyResult();
    }
    final BlockHeader header = block.getHeader();

    return Tracer.processTracing(
            blockchainQueries,
            Optional.of(header),
            traceableState -> {
              ArrayNodeWrapper resultArrayNode = new ArrayNodeWrapper(MAPPER.createArrayNode());
              final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);
              final MainnetTransactionProcessor transactionProcessor =
                  protocolSpec.getTransactionProcessor();
              final TraceBlock.ChainUpdater chainUpdater =
                  new TraceBlock.ChainUpdater(traceableState);

              TransactionSource transactionSource = new TransactionSource(block);
              final LabelledMetric<Counter> outputCounter =
                  new PrometheusMetricsSystem(BesuMetricCategory.DEFAULT_METRIC_CATEGORIES, false)
                      .createLabelledCounter(
                          BesuMetricCategory.BLOCKCHAIN,
                          "transactions_debugtraceblock_pipeline_processed_total",
                          "Number of transactions processed for each block",
                          "step",
                          "action");
              DebugOperationTracer debugOperationTracer = new DebugOperationTracer(traceOptions);
              ExecuteTransactionStep executeTransactionStep =
                  new ExecuteTransactionStep(
                      chainUpdater,
                      transactionProcessor,
                      blockchainQueries.getBlockchain(),
                      debugOperationTracer,
                      protocolSpec,
                      block);
              DebugTraceTransactionStep debugTraceTransactionStep = new DebugTraceTransactionStep();
              BuildArrayNodeCompleterStep buildArrayNodeStep =
                  new BuildArrayNodeCompleterStep(resultArrayNode);
              Pipeline<TransactionTrace> traceBlockPipeline =
                  createPipelineFrom(
                          "getTransactions",
                          transactionSource,
                          4,
                          outputCounter,
                          false,
                          "debug_trace_block_transactions")
                      .thenProcess("executeTransaction", executeTransactionStep)
                      .thenProcessAsyncOrdered(
                          "traceDebugTransaction", debugTraceTransactionStep, 4)
                      .andFinishWith(
                          "buildArrayNode",
                          traceStream -> traceStream.forEachOrdered(buildArrayNodeStep));

              try {
                if (blockchainQueries.getEthScheduler().isPresent()) {
                  blockchainQueries.getEthScheduler().get().startPipeline(traceBlockPipeline).get();
                } else {
                  EthScheduler ethScheduler = new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem());
                  ethScheduler.startPipeline(traceBlockPipeline).get();
                }
              } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
              }

              resultArrayNode = buildArrayNodeStep.getResultArrayNode();
              return Optional.of(resultArrayNode);
            })
        .orElse(emptyResult());
  }

  ArrayNodeWrapper emptyResult() {
    return new ArrayNodeWrapper(MAPPER.createArrayNode());
  }
}
