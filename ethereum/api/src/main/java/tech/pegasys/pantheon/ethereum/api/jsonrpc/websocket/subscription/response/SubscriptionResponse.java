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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.Quantity;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"jsonrpc", "method", "params"})
public class SubscriptionResponse {

  private static final String JSON_RPC_VERSION = "2.0";
  private static final String METHOD_NAME = "eth_subscription";

  private final SubscriptionResponseResult params;

  public SubscriptionResponse(final long subscriptionId, final JsonRpcResult result) {
    this.params = new SubscriptionResponseResult(Quantity.create(subscriptionId), result);
  }

  @JsonGetter("jsonrpc")
  public String getJsonrpc() {
    return JSON_RPC_VERSION;
  }

  @JsonGetter("method")
  public String getMethod() {
    return METHOD_NAME;
  }

  @JsonGetter("params")
  public SubscriptionResponseResult getParams() {
    return params;
  }
}
