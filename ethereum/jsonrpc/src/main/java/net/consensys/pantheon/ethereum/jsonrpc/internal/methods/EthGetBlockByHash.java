package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;

public class EthGetBlockByHash implements JsonRpcMethod {

  private final BlockResultFactory blockResult;
  private final BlockchainQueries blockchain;
  private final JsonRpcParameter parameters;

  public EthGetBlockByHash(
      final BlockchainQueries blockchain,
      final BlockResultFactory blockResult,
      final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.blockResult = blockResult;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getBlockByHash";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    return new JsonRpcSuccessResponse(request.getId(), blockResult(request));
  }

  private BlockResult blockResult(final JsonRpcRequest request) {
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);

    if (isCompleteTransactions(request)) {
      return transactionComplete(hash);
    }

    return transactionHash(hash);
  }

  private BlockResult transactionComplete(final Hash hash) {
    return blockchain.blockByHash(hash).map(tx -> blockResult.transactionComplete(tx)).orElse(null);
  }

  private BlockResult transactionHash(final Hash hash) {
    return blockchain
        .blockByHashWithTxHashes(hash)
        .map(tx -> blockResult.transactionHash(tx))
        .orElse(null);
  }

  private boolean isCompleteTransactions(final JsonRpcRequest request) {
    return parameters.required(request.getParams(), 1, Boolean.class);
  }
}
