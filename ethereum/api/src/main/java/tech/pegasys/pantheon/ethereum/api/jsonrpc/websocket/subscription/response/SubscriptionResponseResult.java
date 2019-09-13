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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.response;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.JsonRpcResult;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"subscription", "result"})
public class SubscriptionResponseResult {

  private final String subscription;
  private final JsonRpcResult result;

  SubscriptionResponseResult(final String subscription, final JsonRpcResult result) {
    this.subscription = subscription;
    this.result = result;
  }

  @JsonGetter("subscription")
  public String getSubscription() {
    return subscription;
  }

  @JsonGetter("result")
  public JsonRpcResult getResult() {
    return result;
  }
}
