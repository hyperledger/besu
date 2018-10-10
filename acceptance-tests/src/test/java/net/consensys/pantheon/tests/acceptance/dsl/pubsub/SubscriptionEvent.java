package net.consensys.pantheon.tests.acceptance.dsl.pubsub;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionEvent {

  private final String version;
  private final String method;
  private final Map<String, String> params;

  @JsonCreator
  public SubscriptionEvent(
      @JsonProperty("jsonrpc") final String version,
      @JsonProperty("method") final String method,
      @JsonProperty("params") final Map<String, String> params) {
    this.version = version;
    this.method = method;
    this.params = params;
  }

  public String getVersion() {
    return version;
  }

  public String getMethod() {
    return method;
  }

  public Map<String, String> getParams() {
    return params;
  }
}
