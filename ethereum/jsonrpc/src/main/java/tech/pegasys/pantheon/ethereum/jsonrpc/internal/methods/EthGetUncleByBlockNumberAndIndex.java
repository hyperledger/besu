package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.UnsignedIntParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.UncleBlockResult;

public class EthGetUncleByBlockNumberAndIndex extends AbstractBlockParameterMethod {

  public EthGetUncleByBlockNumberAndIndex(
      final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    super(blockchain, parameters);
  }

  @Override
  public String getName() {
    return "eth_getUncleByBlockNumberAndIndex";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 0, BlockParameter.class);
  }

  @Override
  protected BlockResult resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final int index =
        parameters().required(request.getParams(), 1, UnsignedIntParameter.class).getValue();
    return blockchainQueries()
        .getOmmer(blockNumber, index)
        .map(UncleBlockResult::build)
        .orElse(null);
  }
}
