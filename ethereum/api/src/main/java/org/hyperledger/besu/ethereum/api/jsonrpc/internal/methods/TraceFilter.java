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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.RewardTraceGenerator;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceFilter extends TraceBlock {

  private static final Logger LOG = LoggerFactory.getLogger(TraceFilter.class);
  private final Long maxRange;

  public TraceFilter(
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final Long maxRange) {
    super(protocolSchedule, blockchainQueries);
    this.maxRange = maxRange;
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_FILTER.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final FilterParameter filterParameter =
        requestContext.getRequiredParameter(0, FilterParameter.class);

    final long fromBlock = resolveBlockNumber(filterParameter.getFromBlock());
    final long toBlock = resolveBlockNumber(filterParameter.getToBlock());
    LOG.trace("Received RPC rpcName={} fromBlock={} toBlock={}", getName(), fromBlock, toBlock);

    if (maxRange > 0 && toBlock - fromBlock > maxRange) {
      LOG.atDebug()
          .setMessage("trace_filter request {} failed:")
          .addArgument(requestContext.getRequest())
          .setCause(
              new IllegalArgumentException(RpcErrorType.EXCEEDS_RPC_MAX_BLOCK_RANGE.getMessage()))
          .log();
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), RpcErrorType.EXCEEDS_RPC_MAX_BLOCK_RANGE);
    }

    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNodeWrapper resultArrayNode =
        new ArrayNodeWrapper(
            mapper.createArrayNode(), filterParameter.getAfter(), filterParameter.getCount());
    if (fromBlock > toBlock)
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(), resultArrayNode.getArrayNode());
    else
      return traceFilterWithPipeline(
          requestContext, filterParameter, fromBlock, toBlock, resultArrayNode);
  }

  private JsonRpcResponse traceFilterWithPipeline(
      final JsonRpcRequestContext requestContext,
      final FilterParameter filterParameter,
      final long fromBlock,
      final long toBlock,
      final ArrayNodeWrapper resultArrayNode) {

    long currentBlockNumber = fromBlock;
    Optional<Block> block =
        blockchainQueriesSupplier.get().getBlockchain().getBlockByNumber(currentBlockNumber);
    while ((block.isEmpty() || block.get().getHeader().getParentHash().equals(Bytes32.ZERO))
        && currentBlockNumber < toBlock) {
      currentBlockNumber++;
      block = blockchainQueriesSupplier.get().getBlockchain().getBlockByNumber(currentBlockNumber);
    }
    if (block.isEmpty()) {
      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(), resultArrayNode.getArrayNode());
    }
    final BlockHeader header = block.get().getHeader();

    List<Block> blockList = getBlockList(currentBlockNumber, toBlock, block);

    ArrayNodeWrapper result =
        Tracer.processTracing(
                getBlockchainQueries(),
                Optional.of(header),
                traceableState -> {
                  TraceFilterSource traceFilterSource =
                      new TraceFilterSource(blockList, resultArrayNode);
                  final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);
                  final MainnetTransactionProcessor transactionProcessor =
                      protocolSpec.getTransactionProcessor();
                  final ChainUpdater chainUpdater = new ChainUpdater(traceableState);
                  final LabelledMetric<Counter> outputCounter =
                      new PrometheusMetricsSystem(
                              BesuMetricCategory.DEFAULT_METRIC_CATEGORIES, false)
                          .createLabelledCounter(
                              BesuMetricCategory.BLOCKCHAIN,
                              "transactions_tracefilter_pipeline_processed_total",
                              "Number of transactions processed for trace_filter",
                              "step",
                              "action");

                  DebugOperationTracer debugOperationTracer =
                      new DebugOperationTracer(new TraceOptions(false, false, true), false);
                  ExecuteTransactionStep executeTransactionStep =
                      new ExecuteTransactionStep(
                          chainUpdater,
                          transactionProcessor,
                          getBlockchainQueries().getBlockchain(),
                          debugOperationTracer,
                          protocolSpec);

                  Function<TransactionTrace, CompletableFuture<Stream<FlatTrace>>>
                      traceFlatTransactionStep =
                          new TraceFlatTransactionStep(
                              protocolSchedule, null, Optional.of(filterParameter));

                  BuildArrayNodeCompleterStep buildArrayNodeStep =
                      new BuildArrayNodeCompleterStep(resultArrayNode);
                  Pipeline<TransactionTrace> traceBlockPipeline =
                      createPipelineFrom(
                              "getTransactions",
                              traceFilterSource,
                              4,
                              outputCounter,
                              false,
                              "trace_block_transactions")
                          .thenProcess("executeTransaction", executeTransactionStep)
                          .thenProcessAsyncOrdered(
                              "traceFlatTransaction", traceFlatTransactionStep, 4)
                          .andFinishWith(
                              "buildArrayNode",
                              traceStream -> traceStream.forEachOrdered(buildArrayNodeStep));

                  try {
                    Optional<EthScheduler> ethSchedulerOpt =
                        getBlockchainQueries().getEthScheduler();

                    ethSchedulerOpt
                        .orElse(new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
                        .startPipeline(traceBlockPipeline)
                        .get();

                  } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                  }
                  return Optional.of(resultArrayNode);
                })
            .orElse(emptyResult());

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result.getArrayNode());
  }

  @Nonnull
  private List<Block> getBlockList(
      final long fromBlock, final long toBlock, final Optional<Block> block) {
    List<Block> blockList = new ArrayList<>();
    Block currentBlock = block.get();
    blockList.add(currentBlock);
    long index = fromBlock + 1; // We already stored current Block
    while (index <= toBlock) {
      Optional<Block> blockByNumber =
          blockchainQueriesSupplier.get().getBlockchain().getBlockByNumber(index);
      blockByNumber.ifPresent(blockList::add);
      index++;
    }
    return blockList;
  }

  public Map<Transaction, Block> createTransactionBlockMap(final List<Block> blockList) {
    Map<Transaction, Block> transactionBlockMap = new HashMap<>();
    for (Block block : blockList) {
      List<Transaction> transactionList = block.getBody().getTransactions();
      for (Transaction transaction : transactionList) {
        transactionBlockMap.put(transaction, block);
      }
    }
    return transactionBlockMap;
  }

  @Override
  protected void generateRewardsFromBlock(
      final Optional<FilterParameter> maybeFilterParameter,
      final Block block,
      final ArrayNodeWrapper resultArrayNode) {
    maybeFilterParameter.ifPresent(
        filterParameter -> {
          final List<Address> fromAddress = filterParameter.getFromAddress();
          if (fromAddress.isEmpty()) {
            final List<Address> toAddress = filterParameter.getToAddress();
            RewardTraceGenerator.generateFromBlock(protocolSchedule, block)
                .map(FlatTrace.class::cast)
                .filter(trace -> trace.getBlockNumber() != 0)
                .filter(
                    trace ->
                        toAddress.isEmpty()
                            || Optional.ofNullable(trace.getAction().getAuthor())
                                .map(Address::fromHexString)
                                .map(toAddress::contains)
                                .orElse(false))
                .forEachOrdered(resultArrayNode::addPOJO);
          }
        });
  }

  private long resolveBlockNumber(final BlockParameter param) {
    if (param.getNumber().isPresent()) {
      return param.getNumber().get();
    } else if (param.isLatest()) {
      return blockchainQueriesSupplier.get().headBlockNumber();
    } else {
      throw new IllegalStateException("Unknown block parameter type.");
    }
  }
}
