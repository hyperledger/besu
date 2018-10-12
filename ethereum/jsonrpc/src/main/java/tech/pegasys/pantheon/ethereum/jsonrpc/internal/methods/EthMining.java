package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.blockcreation.BlockMiner;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class EthMining<C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
    implements JsonRpcMethod {

  private final AbstractMiningCoordinator<C, M> miningCoordinator;

  public EthMining(final AbstractMiningCoordinator<C, M> miningCoordinator) {
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
