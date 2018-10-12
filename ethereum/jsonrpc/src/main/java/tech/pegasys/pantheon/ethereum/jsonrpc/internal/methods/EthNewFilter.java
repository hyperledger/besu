package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.LogsQuery;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.FilterParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class EthNewFilter implements JsonRpcMethod {

  private final FilterManager filterManager;
  private final JsonRpcParameter parameters;

  public EthNewFilter(final FilterManager filterManager, final JsonRpcParameter parameters) {
    this.filterManager = filterManager;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_newFilter";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final FilterParameter filter =
        parameters.required(request.getParams(), 0, FilterParameter.class);
    final LogsQuery query =
        new LogsQuery.Builder().addresses(filter.getAddresses()).topics(filter.getTopics()).build();

    final String logFilterId =
        filterManager.installLogFilter(filter.getFromBlock(), filter.getToBlock(), query);

    return new JsonRpcSuccessResponse(request.getId(), logFilterId);
  }
}
