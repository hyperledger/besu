package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.hyperledger.besu.ethereum.api.util.TraceUtil.arrayNodeFromTraceStream;
import static org.hyperledger.besu.ethereum.api.util.TraceUtil.resultByTransactionHash;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TraceGet implements JsonRpcMethod {
  private final Supplier<BlockTracer> blockTracerSupplier;
  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;

  public TraceGet(
      final Supplier<BlockTracer> blockTracerSupplier,
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule) {
    this.blockTracerSupplier = blockTracerSupplier;
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_GET.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash transactionHash = requestContext.getRequiredParameter(0, Hash.class);
    // TODO: Validate array?
    final List<String> traceNumbers = requestContext.getRequiredParameter(1, List.class);
    final List<Trace> traceList =
        resultByTransactionHash(
                transactionHash, blockchainQueries, blockTracerSupplier, protocolSchedule)
            .collect(Collectors.toList());

    final List<Trace> filteredTraces = new LinkedList<>();
    for (final String traceNumber : traceNumbers) {
      filteredTraces.add(traceList.get(Integer.valueOf(traceNumber, 16) - 1));
    }

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        arrayNodeFromTraceStream(filteredTraces.stream().sequential()));
  }
}
