package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.response;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.JsonRpcResult;

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
