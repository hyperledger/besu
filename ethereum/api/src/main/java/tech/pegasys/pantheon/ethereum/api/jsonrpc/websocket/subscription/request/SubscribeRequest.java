/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.FilterParameter;

import java.util.Objects;

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
        && Objects.equals(includeTransaction, that.includeTransaction)
        && Objects.equals(filterParameter, that.filterParameter)
        && Objects.equals(connectionId, that.connectionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionType, includeTransaction, filterParameter, connectionId);
  }
}
