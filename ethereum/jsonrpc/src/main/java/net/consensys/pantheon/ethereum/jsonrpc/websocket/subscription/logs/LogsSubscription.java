package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.logs;

import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.LogsQuery;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.FilterParameter;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

public class LogsSubscription extends Subscription {

  private final FilterParameter filterParameter;

  public LogsSubscription(final Long subscriptionId, final FilterParameter filterParameter) {
    super(subscriptionId, SubscriptionType.LOGS);
    this.filterParameter = filterParameter;
  }

  public LogsQuery getLogsQuery() {
    return new LogsQuery.Builder()
        .addresses(filterParameter.getAddresses())
        .topics(filterParameter.getTopics())
        .build();
  }
}
