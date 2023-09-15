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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INTERNAL_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TransactionTraceParams;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.PreCloseStateHandler;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugTraceCall extends AbstractTraceCall {
  private static final Logger LOG = LoggerFactory.getLogger(DebugTraceCall.class);

  public DebugTraceCall(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, protocolSchedule, transactionSimulator);
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_TRACE_CALL.getMethodName();
  }

  @Override
  protected TraceOptions getTraceOptions(final JsonRpcRequestContext requestContext) {
    return requestContext
        .getOptionalParameter(1, TransactionTraceParams.class)
        .map(TransactionTraceParams::traceOptions)
        .orElse(TraceOptions.DEFAULT);
  }

  @Override
  protected PreCloseStateHandler<Object> getSimulatorResultHandler(
      final JsonRpcRequestContext requestContext, final DebugOperationTracer tracer) {
    return (mutableWorldState, maybeSimulatorResult) ->
        maybeSimulatorResult.map(
            result -> {
              if (result.isInvalid()) {
                LOG.error(String.format("Invalid simulator result %s", result));
                return new JsonRpcErrorResponse(
                    requestContext.getRequest().getId(), INTERNAL_ERROR);
              }

              final TransactionTrace transactionTrace =
                  new TransactionTrace(
                      result.getTransaction(), result.getResult(), tracer.getTraceFrames());

              return new DebugTraceTransactionResult(transactionTrace);
            });
  }
}
