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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugTraceBlock implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(DebugTraceBlock.class);
  private final BlockHeaderFunctions blockHeaderFunctions;
  private final Supplier<BlockchainQueries> blockchainQueries;
  private final ProtocolSchedule protocolSchedule;
  private final LabelledMetric<Counter> outputCounter;

  public DebugTraceBlock(
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final ObservableMetricsSystem metricsSystem) {
    this.blockHeaderFunctions = ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
    this.blockchainQueries = Suppliers.ofInstance(blockchainQueries);
    ;
    this.protocolSchedule = protocolSchedule;
    this.outputCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "transactions_debugTraceblock_pipeline_processed_total",
            "Number of transactions processed for each block",
            "step",
            "action");
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_BLOCK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Block block;
    try {
      final String input = requestContext.getRequiredParameter(0, String.class);
      block = Block.readFrom(RLP.input(Bytes.fromHexString(input)), this.blockHeaderFunctions);
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.debug("Failed to parse block RLP (index 0)", e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.INVALID_BLOCK_PARAMS);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block params (index 0)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
    final TraceOptions traceOptions;
    try {
      traceOptions =
          requestContext
              .getOptionalParameter(1, TransactionTraceParams.class)
              .map(TransactionTraceParams::traceOptions)
              .orElse(TraceOptions.DEFAULT);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid transaction trace parameter (index 1)",
          RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS,
          e);
    }

    if (blockchainQueries
        .get()
        .getBlockchain()
        .getBlockByHash(block.getHeader().getParentHash())
        .isPresent()) {
      final Collection<DebugTraceTransactionResult> results =
          Tracer.processTracing(
                  blockchainQueries.get(),
                  Optional.of(block.getHeader()),
                  traceableState -> {
                    Collection<DebugTraceTransactionResult> tracesList =
                        new CopyOnWriteArrayList<>();
                    final ProtocolSpec protocolSpec =
                        protocolSchedule.getByBlockHeader(block.getHeader());
                    final MainnetTransactionProcessor transactionProcessor =
                        protocolSpec.getTransactionProcessor();
                    final TraceBlock.ChainUpdater chainUpdater =
                        new TraceBlock.ChainUpdater(traceableState);

                    TransactionSource transactionSource = new TransactionSource(block);
                    DebugOperationTracer debugOperationTracer =
                        new DebugOperationTracer(traceOptions, true);
                    ExecuteTransactionStep executeTransactionStep =
                        new ExecuteTransactionStep(
                            chainUpdater,
                            transactionProcessor,
                            blockchainQueries.get().getBlockchain(),
                            debugOperationTracer,
                            protocolSpec,
                            block);
                    DebugTraceTransactionStep debugTraceTransactionStep =
                        new DebugTraceTransactionStep();
                    Pipeline<TransactionTrace> traceBlockPipeline =
                        createPipelineFrom(
                                "getTransactions",
                                transactionSource,
                                4,
                                outputCounter,
                                false,
                                "debug_trace_block_by_number")
                            .thenProcess("executeTransaction", executeTransactionStep)
                            .thenProcessAsyncOrdered(
                                "debugTraceTransactionStep", debugTraceTransactionStep, 4)
                            .andFinishWith("collect_results", tracesList::add);

                    try {
                      if (blockchainQueries.get().getEthScheduler().isPresent()) {
                        blockchainQueries
                            .get()
                            .getEthScheduler()
                            .get()
                            .startPipeline(traceBlockPipeline)
                            .get();
                      } else {
                        EthScheduler ethScheduler =
                            new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem());
                        ethScheduler.startPipeline(traceBlockPipeline).get();
                      }
                    } catch (InterruptedException | ExecutionException e) {
                      throw new RuntimeException(e);
                    }
                    return Optional.of(tracesList);
                  })
              .orElse(null);
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), results);
    } else {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.PARENT_BLOCK_NOT_FOUND);
    }
  }
}
