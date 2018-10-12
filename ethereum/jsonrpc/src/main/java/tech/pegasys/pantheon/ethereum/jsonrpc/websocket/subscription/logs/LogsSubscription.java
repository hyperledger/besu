package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.logs;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.LogsQuery;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.FilterParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.Subscription;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionType;

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
