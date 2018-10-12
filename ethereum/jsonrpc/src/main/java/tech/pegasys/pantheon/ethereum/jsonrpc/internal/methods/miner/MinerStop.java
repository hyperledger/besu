package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.blockcreation.BlockMiner;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

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
