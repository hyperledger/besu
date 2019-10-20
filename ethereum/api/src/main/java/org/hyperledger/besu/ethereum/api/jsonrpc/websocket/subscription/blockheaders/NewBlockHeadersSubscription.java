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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.blockheaders;

import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.Subscription;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;

public class NewBlockHeadersSubscription extends Subscription {

  private final boolean includeTransactions;

  public NewBlockHeadersSubscription(
      final Long subscriptionId, final String connectionId, final boolean includeTransactions) {
    super(subscriptionId, connectionId, SubscriptionType.NEW_BLOCK_HEADERS, Boolean.FALSE);
    this.includeTransactions = includeTransactions;
  }

  public boolean getIncludeTransactions() {
    return includeTransactions;
  }
}
