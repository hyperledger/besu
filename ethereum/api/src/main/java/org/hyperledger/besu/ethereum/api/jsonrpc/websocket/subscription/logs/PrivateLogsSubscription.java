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

public class PrivateLogsSubscription extends LogsSubscription {

  private final String privacyGroupId;
  private final String privacyUserId;

  public PrivateLogsSubscription(
      final Long subscriptionId,
      final String connectionId,
      final FilterParameter filterParameter,
      final String privacyGroupId,
      final String privacyUserId) {
    super(subscriptionId, connectionId, filterParameter);
    this.privacyGroupId = privacyGroupId;
    this.privacyUserId = privacyUserId;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  public String getPrivacyUserId() {
    return privacyUserId;
  }
}
