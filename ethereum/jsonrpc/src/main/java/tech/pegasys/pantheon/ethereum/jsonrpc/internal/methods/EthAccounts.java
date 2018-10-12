package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class EthAccounts implements JsonRpcMethod {

  @Override
  public String getName() {
    return "eth_accounts";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    // For now, just return an empty list.
    return new JsonRpcSuccessResponse(req.getId(), new Object[] {});
  }
}
