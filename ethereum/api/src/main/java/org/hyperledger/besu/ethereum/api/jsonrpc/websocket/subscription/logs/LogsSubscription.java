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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.logs;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.Subscription;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;

public class LogsSubscription extends Subscription {

  private final FilterParameter filterParameter;

  public LogsSubscription(
      final Long subscriptionId, final String connectionId, final FilterParameter filterParameter) {
    super(subscriptionId, connectionId, SubscriptionType.LOGS, Boolean.FALSE);
    this.filterParameter = filterParameter;
  }

  public FilterParameter getFilterParameter() {
    return filterParameter;
  }
}
