package tech.pegasys.pantheon.ethereum.jsonrpc.internal.response;

public class JsonRpcNoResponse implements JsonRpcResponse {

  @Override
  public JsonRpcResponseType getType() {
    return JsonRpcResponseType.NONE;
  }
}
