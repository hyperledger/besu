package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;

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
