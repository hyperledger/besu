package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

public class InvalidSubscriptionRequestException extends RuntimeException {

  public InvalidSubscriptionRequestException() {
    super();
  }

  public InvalidSubscriptionRequestException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
