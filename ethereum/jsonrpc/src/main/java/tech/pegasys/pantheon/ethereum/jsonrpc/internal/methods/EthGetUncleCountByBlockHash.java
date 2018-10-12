package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

public class EthGetUncleCountByBlockHash implements JsonRpcMethod {

  private final BlockchainQueries blockchain;
  private final JsonRpcParameter parameters;

  public EthGetUncleCountByBlockHash(
      final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getUncleCountByBlockHash";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);
    final String result = blockchain.getOmmerCount(hash).map(Quantity::create).orElse(null);
    return new JsonRpcSuccessResponse(request.getId(), result);
  }
}
