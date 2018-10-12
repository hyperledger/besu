package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders;

import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

public class NewBlockHeadersSubscription extends Subscription {

  private final boolean includeTransactions;

  public NewBlockHeadersSubscription(final Long subscriptionId, final boolean includeTransactions) {
    super(subscriptionId, SubscriptionType.NEW_BLOCK_HEADERS);
    this.includeTransactions = includeTransactions;
  }

  public boolean getIncludeTransactions() {
    return includeTransactions;
  }
}
