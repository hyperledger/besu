package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;

public class EthGetBlockByNumber extends AbstractBlockParameterMethod {

  private final BlockResultFactory blockResult;

  public EthGetBlockByNumber(
      final BlockchainQueries blockchain,
      final BlockResultFactory blockResult,
      final JsonRpcParameter parameters) {
    super(blockchain, parameters);
    this.blockResult = blockResult;
  }

  @Override
  public String getName() {
    return "eth_getBlockByNumber";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    if (isCompleteTransactions(request)) {
      return transactionComplete(blockNumber);
    }

    return transactionHash(blockNumber);
  }

  private BlockResult transactionComplete(final long blockNumber) {
    return blockchainQueries()
        .blockByNumber(blockNumber)
        .map(tx -> blockResult.transactionComplete(tx))
        .orElse(null);
  }

  private BlockResult transactionHash(final long blockNumber) {
    return blockchainQueries()
        .blockByNumberWithTxHashes(blockNumber)
        .map(tx -> blockResult.transactionHash(tx))
        .orElse(null);
  }

  private boolean isCompleteTransactions(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 1, Boolean.class);
  }
}
