package net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.blockcreation.BlockMiner;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class MinerStop<C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
    implements JsonRpcMethod {

  private final AbstractMiningCoordinator<C, M> miningCoordinator;

  public MinerStop(final AbstractMiningCoordinator<C, M> miningCoordinator) {
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
