package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.LogsQuery;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.FilterParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.LogsResult;

public class EthGetLogs implements JsonRpcMethod {

  private final BlockchainQueries blockchain;
  private final JsonRpcParameter parameters;

  public EthGetLogs(final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getLogs";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final FilterParameter filter =
        parameters.required(request.getParams(), 0, FilterParameter.class);
    final LogsQuery query =
        new LogsQuery.Builder()
            .addresses(filter.getAddresses())
            .topics(filter.getTopics().getTopics())
            .build();

    if (isValid(filter)) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    if (filter.getBlockhash() != null) {
      return new JsonRpcSuccessResponse(
          request.getId(), new LogsResult(blockchain.matchingLogs(filter.getBlockhash(), query)));
    }

    final long fromBlockNumber = filter.getFromBlock().getNumber().orElse(0);
    final long toBlockNumber = filter.getToBlock().getNumber().orElse(blockchain.headBlockNumber());

    return new JsonRpcSuccessResponse(
        request.getId(),
        new LogsResult(blockchain.matchingLogs(fromBlockNumber, toBlockNumber, query)));
  }

  private boolean isValid(final FilterParameter filter) {
    return !filter.getFromBlock().isLatest()
        && !filter.getToBlock().isLatest()
        && filter.getBlockhash() != null;
  }
}
