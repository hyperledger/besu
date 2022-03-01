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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceCallManyParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.MixInIgnoreRevertReason;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceCallMany extends TraceCall implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(TraceCallMany.class);
  private static final ObjectMapper MAPPER_IGNORE_REVERT_REASON = new ObjectMapper();

  public TraceCallMany(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, protocolSchedule, transactionSimulator);

    // The trace_call specification does not output the revert reason, so we have to remove it
    MAPPER_IGNORE_REVERT_REASON.addMixIn(FlatTrace.class, MixInIgnoreRevertReason.class);
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_CALL_MANY.getMethodName() : null;
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

    if (requestContext.getRequest().getParamLength() != 2) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final TraceCallManyParameter[] transactionsAndTraceTypeParameters;
    try {
      transactionsAndTraceTypeParameters =
          requestContext.getRequiredParameter(0, TraceCallManyParameter[].class);
    } catch (Exception e) {
      LOG.error("Error parsing trace call many parameter: " + e.getLocalizedMessage());
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Optional<BlockHeader> maybeBlockHeader =
        blockchainQueries.get().getBlockHeaderByNumber(blockNumber);

    if (maybeBlockHeader.isEmpty()) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), BLOCK_NOT_FOUND);
    }

    final List<JsonNode> traceCallResults = new ArrayList<>();

    WorldUpdater updater = transactionSimulator.getWorldUpdater(maybeBlockHeader.get());
    // we have to make sure that the updater has a parent updater (for state diff trace)
    updater = updater.parentUpdater().isPresent() ? updater : updater.updater();
    try {
      final WorldUpdater finalUpdater = updater;
      Arrays.stream(transactionsAndTraceTypeParameters)
          .forEachOrdered(
              p -> {
                try {
                  executeSingleCall(
                      p.getTuple().getJsonCallParameter(),
                      p.getTuple().getTraceTypeParameter(),
                      maybeBlockHeader.get(),
                      finalUpdater,
                      traceCallResults);
                } catch (final TransactioInvalidException e) {
                  return; // TODO: check what OpenEthereum does when on of the calls fails
                }
                finalUpdater.commit();
              });
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    }

    return traceCallResults;
  }

  private void executeSingleCall(
      final JsonCallParameter callParameter,
      final TraceTypeParameter traceTypeParameter,
      final BlockHeader header,
      final WorldUpdater worldUpdater,
      final List<JsonNode> traceCallResults) {
    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final DebugOperationTracer tracer = new DebugOperationTracer(buildTraceOptions(traceTypes));
    final Optional<TransactionSimulatorResult> maybeSimulatorResult =
        transactionSimulator.processWithWorldUpdater(
            callParameter, buildTransactionValidationParams(), tracer, header, worldUpdater);

    LOG.trace("Executing {} call for transaction {}", traceTypeParameter, callParameter);
    if (maybeSimulatorResult.isEmpty()) {
      throw new RuntimeException("Empty simulator result");
    } else {
      if (maybeSimulatorResult.get().isInvalid()) {
        throw new TransactioInvalidException();
      }
      final JsonNode jsonNode = buildResult(traceTypes, tracer, maybeSimulatorResult);
      traceCallResults.add(jsonNode);
    }
  }

  private static class TransactioInvalidException extends RuntimeException {
    TransactioInvalidException() {
      super();
    }
  }
}
