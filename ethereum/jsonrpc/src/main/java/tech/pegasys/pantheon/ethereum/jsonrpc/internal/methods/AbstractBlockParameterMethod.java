package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.OptionalLong;

abstract class AbstractBlockParameterMethod implements JsonRpcMethod {

  private final BlockchainQueries blockchainQueries;
  private final JsonRpcParameter parameters;

  protected AbstractBlockParameterMethod(
      final BlockchainQueries blockchainQueries, final JsonRpcParameter parameters) {
    this.blockchainQueries = blockchainQueries;
    this.parameters = parameters;
  }

  protected abstract BlockParameter blockParameter(JsonRpcRequest request);

  protected abstract Object resultByBlockNumber(JsonRpcRequest request, long blockNumber);

  protected BlockchainQueries blockchainQueries() {
    return blockchainQueries;
  }

  protected JsonRpcParameter parameters() {
    return parameters;
  }

  protected Object pendingResult(final JsonRpcRequest request) {
    // TODO: Update once we mine and better understand pending semantics.
    // This may also be worth always returning null for.
    return null;
  }

  protected Object latestResult(final JsonRpcRequest request) {
    return resultByBlockNumber(request, blockchainQueries.headBlockNumber());
  }

  protected Object findResultByParamType(final JsonRpcRequest request) {
    final BlockParameter blockParam = blockParameter(request);

    final Object result;
    final OptionalLong blockNumber = blockParam.getNumber();
    if (blockNumber.isPresent()) {
      result = resultByBlockNumber(request, blockNumber.getAsLong());
    } else if (blockParam.isLatest()) {
      result = latestResult(request);
    } else {
      // If block parameter is not numeric or latest, it is pending.
      result = pendingResult(request);
    }

    return result;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    return new JsonRpcSuccessResponse(request.getId(), findResultByParamType(request));
  }
}
