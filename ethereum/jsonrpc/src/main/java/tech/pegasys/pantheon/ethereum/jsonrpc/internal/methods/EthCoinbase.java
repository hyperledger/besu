package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

public class EthCoinbase implements JsonRpcMethod {

  private final AbstractMiningCoordinator<?, ?> miningCoordinator;

  public EthCoinbase(final AbstractMiningCoordinator<?, ?> miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public String getName() {
    return "eth_coinbase";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    try {
      final Optional<Address> coinbase = miningCoordinator.getCoinbase();
      if (coinbase.isPresent()) {
        return new JsonRpcSuccessResponse(req.getId(), coinbase.get().toString());
      }
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.COINBASE_NOT_SPECIFIED);
    } catch (UnsupportedOperationException ex) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.INVALID_REQUEST);
    }
  }
}
