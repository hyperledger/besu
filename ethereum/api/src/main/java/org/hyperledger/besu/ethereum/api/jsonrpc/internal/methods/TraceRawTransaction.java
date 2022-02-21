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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.api.util.TraceUtils;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceRawTransaction implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TraceRawTransaction.class);
  private final TransactionSimulator transactionSimulator;
  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;

  public TraceRawTransaction(
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final TransactionSimulator transactionSimulator) {
    this.transactionSimulator = transactionSimulator;
    this.protocolSchedule = protocolSchedule;
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_RAW_TRANSACTION.getMethodName() : null;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 2) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final String rawTransaction = requestContext.getRequiredParameter(0, String.class);

    final Transaction transaction;
    try {
      transaction = DomainObjectDecodeUtils.decodeRawTransaction(rawTransaction);
    } catch (final RLPException | IllegalArgumentException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    LOG.trace("Received local transaction {}", transaction);

    // second param is the trace type/s
    final TraceTypeParameter traceTypeParameter =
        requestContext.getRequiredParameter(1, TraceTypeParameter.class);

    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final DebugOperationTracer tracer = new DebugOperationTracer(buildTraceOptions(traceTypes));
    final Optional<TransactionSimulatorResult> maybeSimulatorResult =
        transactionSimulator.process(
            CallParameter.fromTransaction(transaction),
            buildTransactionValidationParams(),
            tracer,
            blockchainQueries.headBlockNumber());

    if (maybeSimulatorResult.isEmpty()) {
      throw new IllegalStateException("Invalid transaction simulator result.");
    }

    final TransactionTrace transactionTrace =
        new TransactionTrace(
            maybeSimulatorResult.get().getTransaction(),
            maybeSimulatorResult.get().getResult(),
            tracer.getTraceFrames());
    // TODO why do we get the head of the chain here rather than use the block param
    final Block block = blockchainQueries.getBlockchain().getChainHeadBlock();

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        TraceUtils.getTraceCallResult(
            protocolSchedule, traceTypes, maybeSimulatorResult, transactionTrace, block));
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
}
