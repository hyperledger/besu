package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class Web3ClientVersion implements JsonRpcMethod {

  private final String clientVersion;

  public Web3ClientVersion(final String clientVersion) {
    this.clientVersion = clientVersion;
  }

  @Override
  public String getName() {
    return "web3_clientVersion";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    return new JsonRpcSuccessResponse(req.getId(), clientVersion);
  }
}
