package tech.pegasys.pantheon.tests.acceptance.dsl.pubsub;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonRpcSuccessEvent {

  private final String version;
  private final long id;
  private final String result;

  @JsonCreator
  public JsonRpcSuccessEvent(
      @JsonProperty("jsonrpc") final String version,
      @JsonProperty("id") final long id,
      @JsonProperty("result") final String result) {
    this.id = id;
    this.result = result;
    this.version = version;
  }

  public long getId() {
    return id;
  }

  public String getResult() {
    return result;
  }

  public String getVersion() {
    return version;
  }
}
