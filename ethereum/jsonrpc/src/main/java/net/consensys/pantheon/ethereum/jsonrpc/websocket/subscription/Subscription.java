package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription;

import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class Subscription {

  private final Long id;
  private final SubscriptionType subscriptionType;

  public Subscription(final Long id, final SubscriptionType subscriptionType) {
    this.id = id;
    this.subscriptionType = subscriptionType;
  }

  public SubscriptionType getSubscriptionType() {
    return subscriptionType;
  }

  public Long getId() {
    return id;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("subscriptionType", subscriptionType)
        .toString();
  }

  public boolean isType(final SubscriptionType type) {
    return this.subscriptionType == type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Subscription that = (Subscription) o;
    return Objects.equal(id, that.id) && subscriptionType == that.subscriptionType;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, subscriptionType);
  }
}
