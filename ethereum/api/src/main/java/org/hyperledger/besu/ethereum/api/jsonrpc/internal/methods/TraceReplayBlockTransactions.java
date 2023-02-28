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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType.TRACE;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType.VM_TRACE;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TraceFormatter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TraceWriter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff.StateDiffGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceReplayBlockTransactions extends AbstractBlockParameterMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TraceReplayBlockTransactions.class);
  private final Supplier<BlockTracer> blockTracerSupplier;
  private final Supplier<StateDiffGenerator> stateDiffGenerator =
      Suppliers.memoize(StateDiffGenerator::new);
  private final ProtocolSchedule protocolSchedule;

  public TraceReplayBlockTransactions(
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries queries) {
    super(queries);
    this.blockTracerSupplier = blockTracerSupplier;
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
        .orElse(null);
  }

  private Object traceBlock(final Block block, final TraceTypeParameter traceTypeParameter) {

    if (block == null || block.getBody().getTransactions().isEmpty()) {
      return emptyResult();
    }

    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final TraceOptions traceOptions =
        new TraceOptions(false, false, traceTypes.contains(VM_TRACE) || traceTypes.contains(TRACE));

    return blockTracerSupplier
        .get()
        .trace(block, new DebugOperationTracer(traceOptions))
        .map(BlockTrace::getTransactionTraces)
        .map((traces) -> generateTracesFromTransactionTrace(traces, block, traceTypes))
        .orElse(null);
  }

  private JsonNode generateTracesFromTransactionTrace(
      final List<TransactionTrace> transactionTraces,
      final Block block,
      final Set<TraceTypeParameter.TraceType> traceTypes) {
    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNode resultArrayNode = mapper.createArrayNode();
    final AtomicInteger traceCounter = new AtomicInteger(0);
    transactionTraces.forEach(
        transactionTrace ->
            handleTransactionTrace(
                transactionTrace, block, traceTypes, mapper, resultArrayNode, traceCounter));
    return resultArrayNode;
  }

  private void handleTransactionTrace(
      final TransactionTrace transactionTrace,
      final Block block,
      final Set<TraceTypeParameter.TraceType> traceTypes,
      final ObjectMapper mapper,
      final ArrayNode resultArrayNode,
      final AtomicInteger traceCounter) {
    final ObjectNode resultNode = mapper.createObjectNode();

    TransactionProcessingResult result = transactionTrace.getResult();
    resultNode.put("output", result.getOutput().toString());
    result.getRevertReason().ifPresent(r -> resultNode.put("revertReason", r.toHexString()));

    if (traceTypes.contains(TraceType.STATE_DIFF)) {
      generateTracesFromTransactionTrace(
          trace -> resultNode.putPOJO("stateDiff", trace),
          protocolSchedule,
          transactionTrace,
          block,
          (__, txTrace, currentBlock, ignored) ->
              stateDiffGenerator.get().generateStateDiff(txTrace),
          traceCounter);
    }
    setNullNodesIfNotPresent(resultNode, "stateDiff");
    if (traceTypes.contains(TraceTypeParameter.TraceType.TRACE)) {
      generateTracesFromTransactionTrace(
          resultNode.putArray("trace")::addPOJO,
          protocolSchedule,
          transactionTrace,
          block,
          FlatTraceGenerator::generateFromTransactionTrace,
          traceCounter);
    }
    setEmptyArrayIfNotPresent(resultNode, "trace");
    resultNode.put("transactionHash", transactionTrace.getTransaction().getHash().toHexString());
    if (traceTypes.contains(VM_TRACE)) {
      generateTracesFromTransactionTrace(
          trace -> resultNode.putPOJO("vmTrace", trace),
          protocolSchedule,
          transactionTrace,
          block,
          (protocolSchedule, txTrace, currentBlock, ignored) ->
              new VmTraceGenerator(transactionTrace).generateTraceStream(),
          traceCounter);
    }
    setNullNodesIfNotPresent(resultNode, "vmTrace");
    resultArrayNode.add(resultNode);
  }

  private void generateTracesFromTransactionTrace(
      final TraceWriter writer,
      final ProtocolSchedule protocolSchedule,
      final TransactionTrace transactionTrace,
      final Block block,
      final TraceFormatter formatter,
      final AtomicInteger traceCounter) {
    formatter
        .format(protocolSchedule, transactionTrace, block, traceCounter)
        .forEachOrdered(writer::write);
  }

  private void setNullNodesIfNotPresent(final ObjectNode parentNode, final String... keys) {
    Arrays.asList(keys)
        .forEach(
            key ->
                Optional.ofNullable(parentNode.get(key))
                    .ifPresentOrElse(ignored -> {}, () -> parentNode.put(key, (String) null)));
  }

  private void setEmptyArrayIfNotPresent(final ObjectNode parentNode, final String... keys) {
    Arrays.asList(keys)
        .forEach(
            key ->
                Optional.ofNullable(parentNode.get(key))
                    .ifPresentOrElse(ignored -> {}, () -> parentNode.putArray(key)));
  }

  private Object emptyResult() {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper.createArrayNode();
  }
}
