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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.methods;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;

import java.util.HashMap;
import java.util.Map;

public class WebSocketMethodsFactory {

  private final SubscriptionManager subscriptionManager;
  private final Map<String, JsonRpcMethod> jsonRpcMethods;
  private final JsonRpcParameter parameter = new JsonRpcParameter();

  public WebSocketMethodsFactory(
      final SubscriptionManager subscriptionManager,
      final Map<String, JsonRpcMethod> jsonRpcMethods) {
    this.subscriptionManager = subscriptionManager;
    this.jsonRpcMethods = jsonRpcMethods;
  }

  public Map<String, JsonRpcMethod> methods() {
    final Map<String, JsonRpcMethod> websocketMethods = new HashMap<>();
    websocketMethods.putAll(jsonRpcMethods);
    addMethods(
        websocketMethods,
        new EthSubscribe(subscriptionManager, new SubscriptionRequestMapper(parameter)),
        new EthUnsubscribe(subscriptionManager, new SubscriptionRequestMapper(parameter)));
    return websocketMethods;
  }

  public Map<String, JsonRpcMethod> addMethods(
      final Map<String, JsonRpcMethod> methods, final JsonRpcMethod... rpcMethods) {
    for (final JsonRpcMethod rpcMethod : rpcMethods) {
      methods.put(rpcMethod.getName(), rpcMethod);
    }
    return methods;
  }
}
