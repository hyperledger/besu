package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

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
