package net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

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
    final Address coinbase = parameters.required(req.getParams(), 0, Address.class);

    miningCoordinator.setCoinbase(coinbase);

    return new JsonRpcSuccessResponse(req.getId(), true);
  }
}
