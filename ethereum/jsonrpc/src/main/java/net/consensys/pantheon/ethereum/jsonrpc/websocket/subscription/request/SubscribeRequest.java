package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.FilterParameter;

import com.google.common.base.Objects;

public class SubscribeRequest {

  private final SubscriptionType subscriptionType;
  private final Boolean includeTransaction;
  private final FilterParameter filterParameter;
  private final String connectionId;

  public SubscribeRequest(
      final SubscriptionType subscriptionType,
      final FilterParameter filterParameter,
      final Boolean includeTransaction,
      final String connectionId) {
    this.subscriptionType = subscriptionType;
    this.includeTransaction = includeTransaction;
    this.filterParameter = filterParameter;
    this.connectionId = connectionId;
  }

  public SubscriptionType getSubscriptionType() {
    return subscriptionType;
  }

  public FilterParameter getFilterParameter() {
    return filterParameter;
  }

  public Boolean getIncludeTransaction() {
    return includeTransaction;
  }

  public String getConnectionId() {
    return this.connectionId;
  }

  @Override
  public String toString() {
    return "SubscribeRequest{"
        + "subscriptionType="
        + subscriptionType
        + ", includeTransaction="
        + includeTransaction
        + ", filterParameter="
        + filterParameter
        + ", connectionId="
        + connectionId
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SubscribeRequest that = (SubscribeRequest) o;
    return subscriptionType == that.subscriptionType
        && Objects.equal(includeTransaction, that.includeTransaction)
        && Objects.equal(filterParameter, that.filterParameter)
        && Objects.equal(connectionId, that.connectionId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(subscriptionType, includeTransaction, filterParameter, connectionId);
  }
}
