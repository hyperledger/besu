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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.BLOCK_NOT_FOUND;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.MixInIgnoreRevertReason;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;
import java.util.Set;

public class TraceCall extends AbstractTraceByBlock implements JsonRpcMethod {
  public TraceCall(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, protocolSchedule, transactionSimulator);
    // The trace_call specification does not output the revert reason, so we have to remove it
    mapper.addMixIn(FlatTrace.class, MixInIgnoreRevertReason.class);
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_CALL.getMethodName() : null;
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    final Optional<BlockParameter> maybeBlockParameter =
        request.getOptionalParameter(2, BlockParameter.class);

    if (maybeBlockParameter.isPresent()) {
      return maybeBlockParameter.get();
    }

    return BlockParameter.LATEST;
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext requestContext, final long blockNumber) {
    final TraceTypeParameter traceTypeParameter =
        requestContext.getRequiredParameter(1, TraceTypeParameter.class);

    final Optional<BlockHeader> maybeBlockHeader =
        blockchainQueries.get().getBlockHeaderByNumber(blockNumber);

    if (maybeBlockHeader.isEmpty()) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), BLOCK_NOT_FOUND);
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
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    }

    return buildResult(traceTypes, tracer, maybeSimulatorResult.get());
  }

  JsonNode buildResult(
      final Set<TraceTypeParameter.TraceType> traceTypes,
      final DebugOperationTracer tracer,
      final TransactionSimulatorResult simulatorResult) {
    final TransactionTrace transactionTrace =
        new TransactionTrace(
            simulatorResult.getTransaction(), simulatorResult.getResult(), tracer.getTraceFrames());

    return getTraceCallResult(
        protocolSchedule, traceTypes, maybeSimulatorResult, transactionTrace, block);
  }
}
