package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.response;

import net.consensys.pantheon.ethereum.jsonrpc.internal.results.JsonRpcResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

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
