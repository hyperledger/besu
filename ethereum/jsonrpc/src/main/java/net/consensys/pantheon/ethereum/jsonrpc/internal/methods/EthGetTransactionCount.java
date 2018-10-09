package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.PendingTransactions;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

public class EthGetTransactionCount extends AbstractBlockParameterMethod {

  private final PendingTransactions pendingTransactions;

  public EthGetTransactionCount(
      final BlockchainQueries blockchain,
      final PendingTransactions pendingTransactions,
      final JsonRpcParameter parameters) {
    super(blockchain, parameters);
    this.pendingTransactions = pendingTransactions;
  }

  @Override
  public String getName() {
    return "eth_getTransactionCount";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 1, BlockParameter.class);
  }

  @Override
  protected Object pendingResult(final JsonRpcRequest request) {
    final Address address = parameters().required(request.getParams(), 0, Address.class);
    final AtomicReference<Optional<Transaction>> pendingTransaction =
        new AtomicReference<>(Optional.empty());
    final OptionalLong pendingNonce = pendingTransactions.getNextNonceForSender(address);
    if (pendingNonce.isPresent()) {
      return Quantity.create(pendingNonce.getAsLong());
    } else {
      return latestResult(request);
    }
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Address address = parameters().required(request.getParams(), 0, Address.class);
    if (blockNumber > blockchainQueries().headBlockNumber()) {
      return null;
    }
    return Quantity.create(blockchainQueries().getTransactionCount(address, blockNumber));
  }
}
