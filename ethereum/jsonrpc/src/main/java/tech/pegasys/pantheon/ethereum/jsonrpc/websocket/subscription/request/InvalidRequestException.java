package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(final String message) {
    super(message);
  }
}
