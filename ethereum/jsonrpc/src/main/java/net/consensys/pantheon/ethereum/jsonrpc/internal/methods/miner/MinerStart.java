package net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import net.consensys.pantheon.ethereum.blockcreation.CoinbaseNotSetException;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class MinerStart implements JsonRpcMethod {

  private final MiningCoordinator miningCoordinator;

  public MinerStart(final MiningCoordinator miningCoordinator) {
    this.miningCoordinator = miningCoordinator;
  }

  @Override
  public String getName() {
    return "miner_start";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    try {
      miningCoordinator.enable();
    } catch (final CoinbaseNotSetException e) {
      return new JsonRpcErrorResponse(req.getId(), JsonRpcError.COINBASE_NOT_SET);
    }

    return new JsonRpcSuccessResponse(req.getId(), true);
  }
}
