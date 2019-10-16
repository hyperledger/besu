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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TraceFormatter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TraceWriter;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TraceReplayBlockTransactions extends AbstractBlockParameterMethod {
  private static final Logger LOG = LogManager.getLogger();

  private final BlockTracer blockTracer;
  private final ProtocolSchedule<?> protocolSchedule;

  public TraceReplayBlockTransactions(
      final JsonRpcParameter parameters,
      final BlockTracer blockTracer,
      final BlockchainQueries queries,
      final ProtocolSchedule<?> protocolSchedule) {
    super(queries, parameters);
    this.blockTracer = blockTracer;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_REPLAY_BLOCK_TRANSACTIONS.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return getParameters().required(request.getParams(), 0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final TraceTypeParameter traceTypeParameter =
        getParameters().required(request.getParams(), 1, TraceTypeParameter.class);

    // TODO : method returns an error if any option other than “trace” is supplied.
    // remove when others options are implemented
    if (traceTypeParameter.getTraceTypes().contains(TraceTypeParameter.TraceType.STATE_DIFF)
        || traceTypeParameter.getTraceTypes().contains(TraceTypeParameter.TraceType.VM_TRACE)) {
      LOG.warn("Unsupported trace option");
      throw new InvalidJsonRpcParameters("Invalid trace types supplied.");
    }

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
    // TODO: generate options based on traceTypeParameter
    final TraceOptions traceOptions = TraceOptions.DEFAULT;

    return blockTracer
        .trace(block, new DebugOperationTracer(traceOptions))
        .map(BlockTrace::getTransactionTraces)
        .map((traces) -> formatTraces(block.getHeader().getNumber(), traces, traceTypeParameter))
        .orElse(null);
  }

  private JsonNode formatTraces(
      final long blockNumber,
      final List<TransactionTrace> traces,
      final TraceTypeParameter traceTypeParameter) {
    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNode resultArrayNode = mapper.createArrayNode();
    final ObjectNode resultNode = mapper.createObjectNode();
    final AtomicInteger traceCounter = new AtomicInteger(0);
    traces.stream()
        .findFirst()
        .ifPresent(
            transactionTrace -> {
              resultNode.put(
                  "transactionHash", transactionTrace.getTransaction().getHash().getHexString());
              resultNode.put("output", transactionTrace.getResult().getOutput().toString());
            });
    resultNode.put("stateDiff", (String) null);
    resultNode.put("vmTrace", (String) null);

    if (traceTypes.contains(TraceTypeParameter.TraceType.TRACE)) {
      formatTraces(
          blockNumber,
          resultNode.putArray("trace")::addPOJO,
          traces,
          FlatTraceGenerator::generateFromTransactionTrace,
          traceCounter);
    }

    resultArrayNode.add(resultNode);
    return resultArrayNode;
  }

  private void formatTraces(
      final long blockNumber,
      final TraceWriter writer,
      final List<TransactionTrace> traces,
      final TraceFormatter formatter,
      final AtomicInteger traceCounter) {
    traces.forEach(
        (transactionTrace) ->
            formatter
                .format(
                    transactionTrace,
                    traceCounter,
                    protocolSchedule.getByBlockNumber(blockNumber).getGasCalculator())
                .forEachOrdered(writer::write));
  }

  private Object emptyResult() {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper.createArrayNode();
  }
}
