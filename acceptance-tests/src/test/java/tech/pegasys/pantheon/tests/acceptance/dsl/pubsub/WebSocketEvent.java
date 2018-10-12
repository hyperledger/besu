package tech.pegasys.pantheon.tests.acceptance.dsl.pubsub;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketEvent {

  private static final String SUBSCRIPTION_METHOD = "eth_subscription";

  private final boolean subscription;

  @JsonCreator
  public WebSocketEvent(@JsonProperty("method") final String method) {
    this.subscription = SUBSCRIPTION_METHOD.equalsIgnoreCase(method);
  }

  public boolean isSubscription() {
    return subscription;
  }
}
