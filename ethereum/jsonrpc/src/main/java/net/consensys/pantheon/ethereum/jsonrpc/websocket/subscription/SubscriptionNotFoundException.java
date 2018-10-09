package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription;

public class SubscriptionNotFoundException extends RuntimeException {

  private final Long subscriptionId;

  public SubscriptionNotFoundException(final Long subscriptionId) {
    super(String.format("Subscription not found (id=%s)", subscriptionId));
    this.subscriptionId = subscriptionId;
  }

  public Long getSubscriptionId() {
    return subscriptionId;
  }
}
