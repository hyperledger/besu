package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.p2p.api.P2PNetwork;

public class NetListening implements JsonRpcMethod {

  private final P2PNetwork p2pNetwork;

  public NetListening(final P2PNetwork p2pNetwork) {
    this.p2pNetwork = p2pNetwork;
  }

  @Override
  public String getName() {
    return "net_listening";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {
    return new JsonRpcSuccessResponse(req.getId(), p2pNetwork.isListening());
  }
}
