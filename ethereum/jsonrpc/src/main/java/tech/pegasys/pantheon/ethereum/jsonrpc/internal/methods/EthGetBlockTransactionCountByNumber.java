package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

public class EthGetBlockTransactionCountByNumber extends AbstractBlockParameterMethod {

  public EthGetBlockTransactionCountByNumber(
      final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    super(blockchain, parameters);
  }

  @Override
  public String getName() {
    return "eth_getBlockTransactionCountByNumber";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 0, BlockParameter.class);
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest req, final long blockNumber) {
    return blockchainQueries().getTransactionCount(blockNumber).map(Quantity::create).orElse(null);
  }
}
