package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.CallParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessingResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessor;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

import java.util.function.Function;

public class EthEstimateGas implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final TransientTransactionProcessor transientTransactionProcessor;
  private final JsonRpcParameter parameters;

  public EthEstimateGas(
      final BlockchainQueries blockchainQueries,
      final TransientTransactionProcessor transientTransactionProcessor,
      final JsonRpcParameter parameters) {
    this.blockchainQueries = blockchainQueries;
    this.transientTransactionProcessor = transientTransactionProcessor;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_estimateGas";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final CallParameter callParams =
        parameters.required(request.getParams(), 0, CallParameter.class);

    final BlockHeader blockHeader = blockHeader();
    if (blockHeader == null) {
      return errorResponse(request);
    }

    final CallParameter modifiedCallParams =
        overrideGasLimitAndPrice(callParams, blockHeader.getGasLimit());

    return transientTransactionProcessor
        .process(modifiedCallParams, blockHeader.getNumber())
        .map(gasEstimateResponse(request))
        .orElse(errorResponse(request));
  }

  private BlockHeader blockHeader() {
    final long headBlockNumber = blockchainQueries.headBlockNumber();
    return blockchainQueries.getBlockchain().getBlockHeader(headBlockNumber).orElse(null);
  }

  private CallParameter overrideGasLimitAndPrice(
      final CallParameter callParams, final long gasLimit) {
    return new CallParameter(
        callParams.getFrom() != null ? callParams.getFrom().toString() : null,
        callParams.getTo() != null ? callParams.getTo().toString() : null,
        Quantity.create(gasLimit),
        Quantity.create(0L),
        callParams.getValue() != null ? Quantity.create(callParams.getValue()) : null,
        callParams.getPayload() != null ? callParams.getPayload().toString() : null);
  }

  private Function<TransientTransactionProcessingResult, JsonRpcResponse> gasEstimateResponse(
      final JsonRpcRequest request) {
    return result ->
        new JsonRpcSuccessResponse(request.getId(), Quantity.create(result.getGasEstimate()));
  }

  private JsonRpcErrorResponse errorResponse(final JsonRpcRequest request) {
    return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR);
  }
}
