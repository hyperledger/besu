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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType.VM_TRACE;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TraceFormatter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TraceWriter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Suppliers;

public class TraceCall implements JsonRpcMethod {
  private final Supplier<BlockchainQueries> blockchainQueries;
  private final ProtocolSchedule protocolSchedule;
  private final TransactionSimulator transactionSimulator;

  public TraceCall(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = Suppliers.ofInstance(blockchainQueries);
    this.protocolSchedule = protocolSchedule;
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_CALL.getMethodName() : null;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final TraceTypeParameter traceTypeParameter =
        requestContext.getRequiredParameter(1, TraceTypeParameter.class);
    final Optional<BlockParameter> maybeBlockParameter =
        requestContext.getOptionalParameter(2, BlockParameter.class);

    final Optional<BlockHeader> maybeBlockHeader = resolveBlockHeader(maybeBlockParameter);

    if (maybeBlockHeader.isEmpty()) {
      throw new IllegalStateException("Invalid block parameter.");
    }

    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final DebugOperationTracer tracer = new DebugOperationTracer(buildTraceOptions(traceTypes));
    final Optional<TransactionSimulatorResult> maybeSimulatorResult =
        transactionSimulator.process(
            JsonCallParameterUtil.validateAndGetCallParams(requestContext),
            buildTransactionValidationParams(),
            tracer,
            maybeBlockHeader.get());

    if (maybeSimulatorResult.isEmpty()) {
      throw new IllegalStateException("Invalid transaction simulator result.");
    }

    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode resultNode = mapper.createObjectNode();

    resultNode.put("output", maybeSimulatorResult.get().getOutput().toString());

    final TransactionTrace transactionTrace =
        new TransactionTrace(
            maybeSimulatorResult.get().getTransaction(),
            maybeSimulatorResult.get().getResult(),
            tracer.getTraceFrames());

    final Block block = blockchainQueries.get().getBlockchain().getChainHeadBlock();

    setNullNodesIfNotPresent(resultNode, "stateDiff");

    if (traceTypes.contains(TraceTypeParameter.TraceType.TRACE)) {
      generateTracesFromTransactionTrace(
          resultNode.putArray("trace")::addPOJO,
          protocolSchedule,
          transactionTrace,
          block,
          FlatTraceGenerator::generateFromTransactionTrace);
    }
    setEmptyArrayIfNotPresent(resultNode, "trace");

    if (traceTypes.contains(VM_TRACE)) {
      generateTracesFromTransactionTrace(
          trace -> resultNode.putPOJO("vmTrace", trace),
          protocolSchedule,
          transactionTrace,
          block,
          (protocolSchedule, txTrace, currentBlock, ignored) ->
              new VmTraceGenerator(transactionTrace).generateTraceStream());
    }
    setNullNodesIfNotPresent(resultNode, "vmTrace");

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), resultNode);
  }

  private TransactionValidationParams buildTransactionValidationParams() {
    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.transactionSimulator())
        .build();
  }

  private TraceOptions buildTraceOptions(final Set<TraceTypeParameter.TraceType> traceTypes) {
    // TODO: review if mapping of options is correct
    return new TraceOptions(
        traceTypes.contains(TraceTypeParameter.TraceType.STATE_DIFF),
        traceTypes.contains(TraceTypeParameter.TraceType.TRACE),
        traceTypes.contains(TraceTypeParameter.TraceType.VM_TRACE));
  }

  private Optional<BlockHeader> resolveBlockHeader(
      final Optional<BlockParameter> maybeBlockParameter) {
    AtomicLong blockNumber = new AtomicLong();

    maybeBlockParameter.ifPresentOrElse(
        blockParameter -> {
          if (blockParameter.isNumeric()) {
            blockNumber.set(blockParameter.getNumber().get());
          } else if (blockParameter.isEarliest()) {
            blockNumber.set(0);
          } else if (blockParameter.isPending() || blockParameter.isLatest()) {
            blockNumber.set(blockchainQueries.get().headBlockNumber());
          } else {
            throw new IllegalStateException("Unknown block parameter type.");
          }
        },
        () -> blockNumber.set(blockchainQueries.get().headBlockNumber()));

    return blockchainQueries.get().getBlockHeaderByNumber(blockNumber.get());
  }

  private void generateTracesFromTransactionTrace(
      final TraceWriter writer,
      final ProtocolSchedule protocolSchedule,
      final TransactionTrace transactionTrace,
      final Block block,
      final TraceFormatter formatter) {
    formatter
        .format(protocolSchedule, transactionTrace, block, new AtomicInteger(0))
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
}
