package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.UnsignedIntParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.TransactionCompleteResult;

public class EthGetTransactionByBlockNumberAndIndex extends AbstractBlockParameterMethod {

  public EthGetTransactionByBlockNumberAndIndex(
      final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    super(blockchain, parameters);
  }

  @Override
  public String getName() {
    return "eth_getTransactionByBlockNumberAndIndex";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final int index =
        parameters().required(request.getParams(), 1, UnsignedIntParameter.class).getValue();
    final TransactionWithMetadata transactionWithMetadata =
        blockchainQueries().transactionByBlockNumberAndIndex(blockNumber, index);
    return transactionWithMetadata == null
        ? null
        : new TransactionCompleteResult(transactionWithMetadata);
  }
}
