/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INTERNAL_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.PreCloseStateHandler;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;

public class DebugTraceCall extends AbstractTraceCall {

  public DebugTraceCall(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, protocolSchedule, transactionSimulator, true);
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_CALL.getMethodName();
  }

  @Override
  protected TraceOptions getTraceOptions(final JsonRpcRequestContext requestContext) {
    try {
      return requestContext
          .getOptionalParameter(2, TransactionTraceParams.class)
          .map(TransactionTraceParams::traceOptions)
          .orElse(TraceOptions.DEFAULT);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid transaction trace parameter (index 2)",
          RpcErrorType.INVALID_TRANSACTION_TRACE_PARAMS,
          e);
    }
  }

  protected Object resultByBlockHeader(
      final JsonRpcRequestContext request, final BlockHeader header) {
    final JsonCallParameter callParams = JsonCallParameterUtil.validateAndGetCallParams(request);

    final TraceOptions traceOptions = getTraceOptions(request);
    final DebugOperationTracer tracer = new DebugOperationTracer(traceOptions, false);

    TransactionValidationParams validationParams = buildTransactionValidationParams();

    return transactionSimulator
        .process(
            callParams,
            validationParams,
            tracer,
            (mutableWorldState, transactionSimulatorResult) -> {
              return transactionSimulatorResult.map(
                  result -> {
                    if (result.isInvalid()) {
                      return errorResponse(
                          request,
                          JsonRpcErrorConverter.convertTransactionInvalidReason(
                              result.getValidationResult().getInvalidReason()));
                    } else {
                      return new DebugTraceTransactionResult(
                          new TransactionTrace(
                              result.transaction(), result.result(), tracer.getTraceFrames()));
                    }
                  });
            },
            header)
        .orElse(errorResponse(request, RpcErrorType.INTERNAL_ERROR));
  }

  private JsonRpcErrorResponse errorResponse(
      final JsonRpcRequestContext request, final RpcErrorType rpcErrorType) {
    return new JsonRpcErrorResponse(request.getRequest().getId(), new JsonRpcError(rpcErrorType));
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    final Optional<BlockParameter> maybeBlockParameter;
    try {
      maybeBlockParameter = request.getOptionalParameter(1, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block parameter (index 1)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }

    return maybeBlockParameter.orElse(BlockParameter.LATEST);
  }

  @Override
  protected PreCloseStateHandler<Object> getSimulatorResultHandler(
      final JsonRpcRequestContext requestContext, final DebugOperationTracer tracer) {
    return (mutableWorldState, maybeSimulatorResult) ->
        maybeSimulatorResult.map(
            result -> {
              if (result.isInvalid()) {
                final JsonRpcError error =
                    new JsonRpcError(
                        INTERNAL_ERROR, result.getValidationResult().getErrorMessage());
                return new JsonRpcErrorResponse(requestContext.getRequest().getId(), error);
              }

              final TransactionTrace transactionTrace =
                  new TransactionTrace(
                      result.transaction(), result.result(), tracer.getTraceFrames());

              return new DebugTraceTransactionResult(transactionTrace);
            });
  }

  @Override
  protected TransactionValidationParams buildTransactionValidationParams() {
    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.transactionSimulator())
        .isAllowExceedingBalance(true)
        .allowUnderpriced(true)
        .build();
  }
}
