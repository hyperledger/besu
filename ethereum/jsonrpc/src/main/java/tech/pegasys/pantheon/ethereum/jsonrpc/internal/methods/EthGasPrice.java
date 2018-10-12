package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.blockcreation.BlockMiner;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

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
