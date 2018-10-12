package net.consensys.pantheon.ethereum.jsonrpc.internal.response;

public class JsonRpcNoResponse implements JsonRpcResponse {

  @Override
  public JsonRpcResponseType getType() {
    return JsonRpcResponseType.NONE;
  }
}
