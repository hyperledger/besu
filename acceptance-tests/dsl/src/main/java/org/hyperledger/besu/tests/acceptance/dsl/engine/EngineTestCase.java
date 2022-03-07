package org.hyperledger.besu.tests.acceptance.dsl.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public class EngineTestCase {
  private final JsonNode request;
  private final JsonNode response;
  private final int statusCode;

  @JsonCreator
  public EngineTestCase(
      @JsonProperty("request") final JsonNode request,
      @JsonProperty("response") final JsonNode response,
      @JsonProperty("statusCode") final int statusCode) {
    this.request = request;
    this.response = response;
    this.statusCode = statusCode;
  }

  public JsonNode getRequest() {
    return request;
  }

  public JsonNode getResponse() {
    return response;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
