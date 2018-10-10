package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class EthMining implements JsonRpcMethod {

  private final MiningCoordinator miningCoordinator;

  public EthMining(final MiningCoordinator miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public String getName() {
    return "eth_mining";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    return new JsonRpcSuccessResponse(req.getId(), miningCoordinator.isRunning());
  }
}
