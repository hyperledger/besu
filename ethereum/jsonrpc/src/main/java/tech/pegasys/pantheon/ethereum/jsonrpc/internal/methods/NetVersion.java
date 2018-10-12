package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

/**
 * In Consensys' client, net_version maps to the network id, as specified in *
 * https://github.com/ethereum/wiki/wiki/JSON-RPC#net_version
 *
 * <p>This method can be deprecated in the future, @see https://github.com/ethereum/EIPs/issues/611
 */
public class NetVersion implements JsonRpcMethod {
  private final String chainId;

  public NetVersion(final String chainId) {
    this.chainId = chainId;
  }

  @Override
  public String getName() {
    return "net_version";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    return new JsonRpcSuccessResponse(req.getId(), chainId);
  }
}
