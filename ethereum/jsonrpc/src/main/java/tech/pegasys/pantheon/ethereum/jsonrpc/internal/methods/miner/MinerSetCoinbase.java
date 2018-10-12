package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class MinerSetCoinbase implements JsonRpcMethod {

  private final AbstractMiningCoordinator<?, ?> miningCoordinator;
  private final JsonRpcParameter parameters;

  public MinerSetCoinbase(
      final AbstractMiningCoordinator<?, ?> miningCoordinator, final JsonRpcParameter parameters) {
    this.miningCoordinator = miningCoordinator;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "miner_setCoinbase";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    try {
      final Address coinbase = parameters.required(req.getParams(), 0, Address.class);
      miningCoordinator.setCoinbase(coinbase);
      return new JsonRpcSuccessResponse(req.getId(), true);
    } catch (UnsupportedOperationException ex) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.INVALID_REQUEST);
    }
  }
}
