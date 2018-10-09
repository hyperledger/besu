package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing;

import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

public class SyncingSubscription extends Subscription {
  private boolean firstMessageHasBeenSent = false;

  public SyncingSubscription(final Long id, final SubscriptionType subscriptionType) {
    super(id, subscriptionType);
  }

  public void setFirstMessageHasBeenSent(final boolean firstMessageHasBeenSent) {
    this.firstMessageHasBeenSent = firstMessageHasBeenSent;
  }

  public boolean isFirstMessageHasBeenSent() {
    return firstMessageHasBeenSent;
  }
}
