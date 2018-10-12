package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.UnsignedIntParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionCompleteResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionResult;

public class EthGetTransactionByBlockHashAndIndex implements JsonRpcMethod {

  private final BlockchainQueries blockchain;
  private final JsonRpcParameter parameters;

  public EthGetTransactionByBlockHashAndIndex(
      final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getTransactionByBlockHashAndIndex";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);
    final int index =
        parameters.required(request.getParams(), 1, UnsignedIntParameter.class).getValue();
    final TransactionWithMetadata transactionWithMetadata =
        blockchain.transactionByBlockHashAndIndex(hash, index);
    final TransactionResult result =
        transactionWithMetadata == null
            ? null
            : new TransactionCompleteResult(transactionWithMetadata);

    return new JsonRpcSuccessResponse(request.getId(), result);
  }
}
