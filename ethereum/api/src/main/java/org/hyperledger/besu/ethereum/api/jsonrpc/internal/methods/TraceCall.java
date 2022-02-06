/*
 * Copyright Hyperledger Besu.
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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TraceCallResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff.StateDiffGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff.StateDiffTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.MixInIgnoreRevertReason;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTrace;
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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;

public class TraceCall implements JsonRpcMethod {
  private final Supplier<BlockchainQueries> blockchainQueries;
  private final ProtocolSchedule protocolSchedule;
  private final TransactionSimulator transactionSimulator;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public TraceCall(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    this.blockchainQueries = Suppliers.ofInstance(blockchainQueries);
    this.protocolSchedule = protocolSchedule;
    this.transactionSimulator = transactionSimulator;

    // OpenEthereum does not output the revert reason in the trace, so we have to remove it
    OBJECT_MAPPER.addMixIn(FlatTrace.class, MixInIgnoreRevertReason.class);
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

    final TransactionTrace transactionTrace =
        new TransactionTrace(
            maybeSimulatorResult.get().getTransaction(),
            maybeSimulatorResult.get().getResult(),
            tracer.getTraceFrames());

    final Block block = blockchainQueries.get().getBlockchain().getChainHeadBlock();

    final TraceCallResult.Builder builder = TraceCallResult.builder();

    transactionTrace
        .getResult()
        .getRevertReason()
        .ifPresentOrElse(
            revertReason -> builder.output(revertReason.toHexString()),
            () -> builder.output(maybeSimulatorResult.get().getOutput().toString()));

    if (traceTypes.contains(TraceTypeParameter.TraceType.STATE_DIFF)) {
      new StateDiffGenerator()
          .generateStateDiff(transactionTrace)
          .forEachOrdered(stateDiff -> builder.stateDiff((StateDiffTrace) stateDiff));
    }

    if (traceTypes.contains(TraceTypeParameter.TraceType.TRACE)) {
      FlatTraceGenerator.generateFromTransactionTrace(
              protocolSchedule, transactionTrace, block, new AtomicInteger(), false)
          .forEachOrdered(trace -> builder.addTrace((FlatTrace) trace));
    }

    if (traceTypes.contains(VM_TRACE)) {
      new VmTraceGenerator(transactionTrace)
          .generateTraceStream()
          .forEachOrdered(vmTrace -> builder.vmTrace((VmTrace) vmTrace));
    }

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), OBJECT_MAPPER.valueToTree(builder.build()));
  }

  private TransactionValidationParams buildTransactionValidationParams() {
    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.transactionSimulator())
        .build();
  }

  private TraceOptions buildTraceOptions(final Set<TraceTypeParameter.TraceType> traceTypes) {
    return new TraceOptions(
        traceTypes.contains(TraceTypeParameter.TraceType.STATE_DIFF),
        false,
        traceTypes.contains(TraceTypeParameter.TraceType.TRACE)
            || traceTypes.contains(TraceTypeParameter.TraceType.VM_TRACE));
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
}
