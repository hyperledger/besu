package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.LogWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.LogsResult;

import java.util.List;

public class EthGetFilterLogs implements JsonRpcMethod {

  private final FilterManager filterManager;
  private final JsonRpcParameter parameters;

  public EthGetFilterLogs(final FilterManager filterManager, final JsonRpcParameter parameters) {
    this.filterManager = filterManager;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getFilterLogs";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final String filterId = parameters.required(request.getParams(), 0, String.class);

    final List<LogWithMetadata> logs = filterManager.logs(filterId);
    if (logs != null) {
      return new JsonRpcSuccessResponse(request.getId(), new LogsResult(logs));
    }

    return new JsonRpcErrorResponse(request.getId(), JsonRpcError.FILTER_NOT_FOUND);
  }
}
