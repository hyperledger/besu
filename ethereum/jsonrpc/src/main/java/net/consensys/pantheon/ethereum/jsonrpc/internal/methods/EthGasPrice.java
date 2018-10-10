package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.blockcreation.BlockMiner;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

public class EthGasPrice<C, M extends BlockMiner<C, ? extends AbstractBlockCreator<C>>>
    implements JsonRpcMethod {

  private final AbstractMiningCoordinator<C, M> miningCoordinator;

  public EthGasPrice(final AbstractMiningCoordinator<C, M> miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public String getName() {
    return "eth_gasPrice";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    Wei gasPrice;
    Object result = null;
    gasPrice = miningCoordinator.getMinTransactionGasPrice();
    if (gasPrice != null) {
      result = Quantity.create(gasPrice.toLong());
    }
    return new JsonRpcSuccessResponse(req.getId(), result);
  }
}
