package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionReceiptWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptRootResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.TransactionReceiptStatusResult;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionReceiptType;

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
