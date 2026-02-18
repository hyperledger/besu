/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.StreamingJsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class DebugTraceBlockByNumber extends AbstractBlockParameterMethod {

  protected final ProtocolSchedule protocolSchedule;
  private final LabelledMetric<Counter> outputCounter;
  private final EthScheduler ethScheduler;

  public DebugTraceBlockByNumber(
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final ObservableMetricsSystem metricsSystem,
      final EthScheduler ethScheduler) {
    super(blockchainQueries);
    this.protocolSchedule = protocolSchedule;
    this.outputCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "transactions_debugtraceblock_pipeline_processed_total",
            "Number of transactions processed for each block",
            "step",
            "action");
    this.ethScheduler = ethScheduler;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_BLOCK_BY_NUMBER.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    try {
      return request.getRequiredParameter(0, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block parameter (index 0)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final BlockParameter blockParam = blockParameter(requestContext);
    final Optional<Long> blockNumber = blockParam.getNumber();
    final long resolvedBlockNumber;

    if (blockNumber.isPresent()) {
      resolvedBlockNumber = blockNumber.get();
    } else if (blockParam.isLatest()) {
      resolvedBlockNumber = getBlockchainQueries().headBlockNumber();
    } else if (blockParam.isFinalized()) {
      resolvedBlockNumber =
          getBlockchainQueries().finalizedBlockHeader().map(h -> h.getNumber()).orElse(-1L);
    } else if (blockParam.isSafe()) {
      resolvedBlockNumber =
          getBlockchainQueries().safeBlockHeader().map(h -> h.getNumber()).orElse(-1L);
    } else {
      resolvedBlockNumber = getBlockchainQueries().headBlockNumber();
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
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonRpcParameters(
          e.getMessage(), RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS, e);
    }

    Optional<Block> maybeBlock =
        getBlockchainQueries().getBlockchain().getBlockByNumber(resolvedBlockNumber);

    StreamingJsonRpcSuccessResponse.ResultWriter resultWriter =
        generator -> {
          if (maybeBlock.isEmpty()) {
            generator.writeNull();
            return;
          }
          final Block block = maybeBlock.get();

          Tracer.processTracing(
              getBlockchainQueries(),
              Optional.of(block.getHeader()),
              traceableState -> {
                final ProtocolSpec protocolSpec =
                    protocolSchedule.getByBlockHeader(block.getHeader());
                final MainnetTransactionProcessor transactionProcessor =
                    protocolSpec.getTransactionProcessor();
                final TraceBlock.ChainUpdater chainUpdater =
                    new TraceBlock.ChainUpdater(traceableState);

                TransactionSource transactionSource = new TransactionSource(block);
                DebugOperationTracer debugOperationTracer =
                    new DebugOperationTracer(traceOptions.opCodeTracerConfig(), true);
                ExecuteTransactionStep executeTransactionStep =
                    new ExecuteTransactionStep(
                        chainUpdater,
                        transactionProcessor,
                        getBlockchainQueries().getBlockchain(),
                        debugOperationTracer,
                        protocolSpec,
                        block);

                Consumer<DebugTraceTransactionResult> streamWriter =
                    result -> {
                      try {
                        generator.writeObject(result);
                        generator.flush();
                      } catch (IOException e) {
                        throw new UncheckedIOException(e);
                      }
                    };

                try {
                  generator.writeStartArray();
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }

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
                            "debugTraceTransactionStep",
                            DebugTraceTransactionStepFactory.createAsync(
                                traceOptions, protocolSpec),
                            4)
                        .andFinishWith("stream_results", streamWriter);

                try {
                  ethScheduler.startPipeline(traceBlockPipeline).get();
                  generator.writeEndArray();
                } catch (InterruptedException | ExecutionException | IOException e) {
                  throw new RuntimeException(e);
                }
                return Optional.empty();
              });
        };

    return new StreamingJsonRpcSuccessResponse(
        requestContext.getRequest().getId(), resultWriter);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    // Not used — response() is overridden to use streaming
    throw new UnsupportedOperationException("Use response() directly");
  }
}
