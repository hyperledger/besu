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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request;

import org.hyperledger.besu.ethereum.api.query.LogsQuery;

import java.util.Objects;

public class SubscribeRequest {

  private final SubscriptionType subscriptionType;
  private final Boolean includeTransaction;
  private final LogsQuery logsQuery;
  private final String connectionId;

  public SubscribeRequest(
      final SubscriptionType subscriptionType,
      final LogsQuery logsQuery,
      final Boolean includeTransaction,
      final String connectionId) {
    this.subscriptionType = subscriptionType;
    this.includeTransaction = includeTransaction;
    this.logsQuery = logsQuery;
    this.connectionId = connectionId;
  }

  public SubscriptionType getSubscriptionType() {
    return subscriptionType;
  }

  public LogsQuery getLogsQuery() {
    return logsQuery;
  }

  public Boolean getIncludeTransaction() {
    return includeTransaction;
  }

  public String getConnectionId() {
    return this.connectionId;
  }

  @Override
  public String toString() {
    return String.format(
        "SubscribeRequest{subscriptionType=%s, includeTransaction=%s, logsQuery=%s, connectionId=%s}",
        subscriptionType, includeTransaction, logsQuery, connectionId);
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
        && Objects.equals(logsQuery, that.logsQuery)
        && Objects.equals(connectionId, that.connectionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subscriptionType, includeTransaction, logsQuery, connectionId);
  }
}
