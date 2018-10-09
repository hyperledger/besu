package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptRootResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptStatusResult;
import net.consensys.pantheon.ethereum.mainnet.TransactionReceiptType;

public class EthGetTransactionReceipt implements JsonRpcMethod {

  private final BlockchainQueries blockchain;
  private final JsonRpcParameter parameters;

  public EthGetTransactionReceipt(
      final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    this.blockchain = blockchain;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_getTransactionReceipt";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);
    final TransactionReceiptResult result =
        blockchain
            .transactionReceiptByTransactionHash(hash)
            .map(receipt -> getResult(receipt))
            .orElse(null);
    return new JsonRpcSuccessResponse(request.getId(), result);
  }

  private TransactionReceiptResult getResult(final TransactionReceiptWithMetadata receipt) {
    if (receipt.getReceipt().getTransactionReceiptType() == TransactionReceiptType.ROOT) {
      return new TransactionReceiptRootResult(receipt);
    } else {
      return new TransactionReceiptStatusResult(receipt);
    }
  }
}
