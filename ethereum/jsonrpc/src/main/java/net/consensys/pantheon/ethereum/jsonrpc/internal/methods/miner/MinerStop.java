package net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class MinerStop implements JsonRpcMethod {

  private final MiningCoordinator miningCoordinator;

  public MinerStop(final MiningCoordinator miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public String getName() {
    return "miner_stop";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    if (miningCoordinator != null) {
      miningCoordinator.disable();
    }

    return new JsonRpcSuccessResponse(req.getId(), true);
  }
}
