package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static net.consensys.pantheon.ethereum.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.CallParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessor;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class EthCall extends AbstractBlockParameterMethod {

  private final TransientTransactionProcessor transientTransactionProcessor;

  public EthCall(
      final BlockchainQueries blockchainQueries,
      final TransientTransactionProcessor transientTransactionProcessor,
      final JsonRpcParameter parameters) {
    super(blockchainQueries, parameters);
    this.transientTransactionProcessor = transientTransactionProcessor;
  }

  @Override
  public String getName() {
    return "eth_call";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 1, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final CallParameter callParams = validateAndGetCallParams(request);

    return transientTransactionProcessor
        .process(callParams, blockNumber)
        .map(
            result ->
                result
                    .getValidationResult()
                    .either(
                        (() ->
                            new JsonRpcSuccessResponse(
                                request.getId(), result.getOutput().toString())),
                        reason ->
                            new JsonRpcErrorResponse(
                                request.getId(), convertTransactionInvalidReason(reason))))
        .orElse(validRequestBlockNotFound(request));
  }

  private JsonRpcSuccessResponse validRequestBlockNotFound(final JsonRpcRequest request) {
    return new JsonRpcSuccessResponse(request.getId(), null);
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    return (JsonRpcResponse) findResultByParamType(request);
  }

  private CallParameter validateAndGetCallParams(final JsonRpcRequest request) {
    final CallParameter callParams =
        parameters().required(request.getParams(), 0, CallParameter.class);
    if (callParams.getTo() == null) {
      throw new InvalidJsonRpcParameters("Missing \"to\" field in call arguments");
    }
    return callParams;
  }
}
