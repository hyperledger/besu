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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceBlock.ChainUpdater;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TraceReplayResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.services.pipeline.Pipeline;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceReplayBlockTransactions extends AbstractBlockParameterMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TraceReplayBlockTransactions.class);
  private final EthScheduler ethScheduler = new EthScheduler(4, 4, 4, 4, new NoOpMetricsSystem());
  private final ProtocolSchedule protocolSchedule;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public TraceReplayBlockTransactions(
      final ProtocolSchedule protocolSchedule, final BlockchainQueries queries) {
    super(queries);
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_REPLAY_BLOCK_TRANSACTIONS.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final TraceTypeParameter traceTypeParameter =
        request.getRequiredParameter(1, TraceTypeParameter.class);

    LOG.trace(
        "Received RPC rpcName={} block={} traceType={}",
        getName(),
        blockNumber,
        traceTypeParameter);

    if (blockNumber == BlockHeader.GENESIS_BLOCK_NUMBER) {
      // Nothing to trace for the genesis block
      return emptyResult();
    }

    return getBlockchainQueries()
        .getBlockchain()
        .getBlockByNumber(blockNumber)
        .map((block) -> traceBlock(block, traceTypeParameter))
        .map(ArrayNodeWrapper::getArrayNode)
        .orElse(null);
  }

  private ArrayNodeWrapper traceBlock(
      final Block block, final TraceTypeParameter traceTypeParameter) {

    ArrayNodeWrapper resultArrayNode = new ArrayNodeWrapper(MAPPER.createArrayNode());
    if (block == null) {
      return resultArrayNode;
    }

    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();

    final BlockHeader header = block.getHeader();
    final BlockHeader previous =
        getBlockchainQueries()
            .getBlockchain()
            .getBlockHeader(block.getHeader().getParentHash())
            .orElse(null);

    if (previous == null) {
      return resultArrayNode;
    }

    try (final var worldState =
        getBlockchainQueries()
            .getWorldStateArchive()
            .getMutable(previous.getStateRoot(), previous.getBlockHash(), false)
            .map(
                ws -> {
                  if (!ws.isPersistable()) {
                    return ws.copy();
                  }
                  return ws;
                })
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Missing worldstate for stateroot "
                            + previous.getStateRoot().toShortHexString()))) {

      final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);
      final MainnetTransactionProcessor transactionProcessor =
          protocolSpec.getTransactionProcessor();
      final ChainUpdater chainUpdater = new ChainUpdater(worldState);

      final TransactionSource transactionSource = new TransactionSource(block);
      final LabelledMetric<Counter> outputCounter =
          new PrometheusMetricsSystem(BesuMetricCategory.DEFAULT_METRIC_CATEGORIES, false)
              .createLabelledCounter(
                  BesuMetricCategory.BLOCKCHAIN,
                  "transactions_traceblock_pipeline_processed_total",
                  "Number of transactions processed for each block",
                  "step",
                  "action");
      final DebugOperationTracer debugOperationTracer =
          new DebugOperationTracer(new TraceOptions(false, false, true));
      final ExecuteTransactionStep executeTransactionStep =
          new ExecuteTransactionStep(
              chainUpdater,
              block,
              transactionProcessor,
              getBlockchainQueries().getBlockchain(),
              debugOperationTracer,
              protocolSpec);

      final Function<TransactionTrace, CompletableFuture<TraceReplayResult>>
          traceReplayTransactionStep =
              new TraceReplayTransactionStep(protocolSchedule, block, traceTypes);

      final BuildArrayNodeCompleterStep buildArrayNodeStep =
          new BuildArrayNodeCompleterStep(resultArrayNode);

      final Pipeline<Transaction> traceBlockPipeline =
          createPipelineFrom(
                  "getTransactions",
                  transactionSource,
                  4,
                  outputCounter,
                  false,
                  "trace_replay_block_transactions")
              .thenProcess("executeTransaction", executeTransactionStep)
              .thenProcessAsyncOrdered("traceReplayTransaction", traceReplayTransactionStep, 4)
              .andFinishWith("buildArrayNode", buildArrayNodeStep::accept);
      try {
        ethScheduler.startPipeline(traceBlockPipeline).get();
      } catch (final InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      resultArrayNode = buildArrayNodeStep.getResultArrayNode();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return resultArrayNode;
  }

  private Object emptyResult() {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper.createArrayNode();
  }
}
