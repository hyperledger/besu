/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription;

import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class Subscription {

  private final Long subscriptionId;
  private final String connectionId;
  private final SubscriptionType subscriptionType;
  private final Boolean includeTransaction;

  public Subscription(
      final Long subscriptionId,
      final String connectionId,
      final SubscriptionType subscriptionType,
      final Boolean includeTransaction) {
    this.subscriptionId = subscriptionId;
    this.connectionId = connectionId;
    this.subscriptionType = subscriptionType;
    this.includeTransaction = includeTransaction;
  }

  public SubscriptionType getSubscriptionType() {
    return subscriptionType;
  }

  public Long getSubscriptionId() {
    return subscriptionId;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public Boolean getIncludeTransaction() {
    return includeTransaction;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subscriptionId", subscriptionId)
        .add("connectionId", connectionId)
        .add("subscriptionType", subscriptionType)
        .add("includeTransaction", includeTransaction)
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
    return Objects.equals(subscriptionId, that.subscriptionId)
        && subscriptionType == that.subscriptionType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionId, subscriptionType);
  }
}
