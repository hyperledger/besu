package tech.pegasys.pantheon.ethereum.jsonrpc;

public class JsonRpcServiceException extends RuntimeException {

  public JsonRpcServiceException(final String message) {
    super(message);
  }
}
