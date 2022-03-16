/*
 * Copyright Hyperledger Besu Contributors.
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
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceCallManyParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceCallMany extends TraceCall implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(TraceCallMany.class);

  public TraceCallMany(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, protocolSchedule, transactionSimulator);
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
      final String blockNumberString = String.valueOf(blockNumber);
      traceLambda(
          LOG,
          "Received RPC rpcName={} trace_callManyParams={} block={}",
          this::getName,
          transactionsAndTraceTypeParameters::toString,
          blockNumberString::toString);
    } catch (final Exception e) {
      LOG.error("Error parsing trace_callMany parameters: {}", e.getLocalizedMessage());
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Optional<BlockHeader> maybeBlockHeader =
        blockchainQueriesSupplier.get().getBlockHeaderByNumber(blockNumber);

    if (maybeBlockHeader.isEmpty()) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), BLOCK_NOT_FOUND);
    }
    final BlockHeader blockHeader = maybeBlockHeader.get();

    final List<JsonNode> traceCallResults = new ArrayList<>();
    final WorldUpdater updater = transactionSimulator.getWorldUpdater(blockHeader);
    try {
      Arrays.stream(transactionsAndTraceTypeParameters)
          .forEachOrdered(
              param -> {
                final WorldUpdater localUpdater = updater.updater();
                traceCallResults.add(
                    getSingleCallResult(
                        param.getTuple().getJsonCallParameter(),
                        param.getTuple().getTraceTypeParameter(),
                        blockHeader,
                        localUpdater));
                localUpdater.commit();
              });
    } catch (final TransactionInvalidException e) {
      LOG.error("Invalid transaction simulator result");
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    } catch (final EmptySimulatorResultException e) {
      LOG.error(
          "Empty simulator result, call params: {}, blockHeader: {} ",
          JsonCallParameterUtil.validateAndGetCallParams(requestContext),
          blockHeader);
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    } catch (final Exception e) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    }
    return traceCallResults;
  }

  private JsonNode getSingleCallResult(
      final JsonCallParameter callParameter,
      final TraceTypeParameter traceTypeParameter,
      final BlockHeader header,
      final WorldUpdater worldUpdater) {
    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final DebugOperationTracer tracer = new DebugOperationTracer(buildTraceOptions(traceTypes));
    final Optional<TransactionSimulatorResult> maybeSimulatorResult =
        transactionSimulator.processWithWorldUpdater(
            callParameter, buildTransactionValidationParams(), tracer, header, worldUpdater);

    LOG.trace("Executing {} call for transaction {}", traceTypeParameter, callParameter);
    if (maybeSimulatorResult.isEmpty()) {
      throw new EmptySimulatorResultException();
    }
    final TransactionSimulatorResult simulatorResult = maybeSimulatorResult.get();
    if (simulatorResult.isInvalid()) {
      throw new TransactionInvalidException();
    }

    final TransactionTrace transactionTrace =
        new TransactionTrace(
            simulatorResult.getTransaction(), simulatorResult.getResult(), tracer.getTraceFrames());

    final Block block = blockchainQueriesSupplier.get().getBlockchain().getChainHeadBlock();

    return getTraceCallResult(
        protocolSchedule, traceTypes, maybeSimulatorResult, transactionTrace, block);
  }

  private static class TransactionInvalidException extends RuntimeException {
    TransactionInvalidException() {
      super();
    }
  }

  private static class EmptySimulatorResultException extends RuntimeException {
    EmptySimulatorResultException() {
      super();
    }
  }
}
