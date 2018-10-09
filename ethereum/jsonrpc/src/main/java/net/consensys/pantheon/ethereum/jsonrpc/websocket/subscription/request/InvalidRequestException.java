package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(final String message) {
    super(message);
  }
}
