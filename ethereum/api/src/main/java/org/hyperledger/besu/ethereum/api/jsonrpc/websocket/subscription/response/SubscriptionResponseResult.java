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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.response;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"subscription", "privacyGroupId", "result"})
public class SubscriptionResponseResult {

  private final String subscription;
  private final JsonRpcResult result;

  @JsonInclude(Include.NON_NULL)
  private final String privacyGroupId;

  SubscriptionResponseResult(final String subscription, final JsonRpcResult result) {
    this(subscription, result, null);
  }

  SubscriptionResponseResult(
      final String subscription, final JsonRpcResult result, final String privacyGroupId) {
    this.subscription = subscription;
    this.result = result;
    this.privacyGroupId = privacyGroupId;
  }

  @JsonGetter("subscription")
  public String getSubscription() {
    return subscription;
  }

  @JsonGetter("result")
  public JsonRpcResult getResult() {
    return result;
  }

  @JsonGetter("privacyGroupId")
  public String getPrivacyGroupId() {
    return privacyGroupId;
  }
}
