package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
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

public class TraceRawTransaction implements JsonRpcMethod
{
  private static final Logger LOG = LoggerFactory.getLogger(TraceRawTransaction.class);
  private final TransactionSimulator transactionSimulator;

  public TraceRawTransaction(
      final TransactionSimulator transactionSimulator) {
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_RAW_TRANSACTION.getMethodName() : null;
  }

  @Override
  public JsonRpcResponse response(JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 2) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    // TODO get blockNumber from blockchainqueries
    final long blockNumber = 0L;

    final String rawTransaction = requestContext.getRequiredParameter(0, String.class);

    final Transaction transaction;
    try {
      transaction = DomainObjectDecodeUtils.decodeRawTransaction(rawTransaction);
    } catch (final RLPException | IllegalArgumentException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    LOG.trace("Received local transaction {}", transaction);

    // TODO turn tx into callParams

    final TraceTypeParameter traceTypeParameter =
        requestContext.getRequiredParameter(1, TraceTypeParameter.class);

    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final DebugOperationTracer tracer = new DebugOperationTracer(buildTraceOptions(traceTypes));
    final Optional<TransactionSimulatorResult> maybeSimulatorResult =
        transactionSimulator.process(CallParameter.fromTransaction(transaction),
            buildTransactionValidationParams(),
            tracer,
            blockNumber);

    if (maybeSimulatorResult.isEmpty()) {
      throw new IllegalStateException("Invalid transaction simulator result.");
    }
//    return MAPPER_IGNORE_REVERT_REASON.valueToTree(builder.build());
    return null;
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