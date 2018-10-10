package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class LogsSubscriptionParam {

  private final String address;
  private final List<String> topics;

  @JsonCreator
  LogsSubscriptionParam(
      @JsonProperty("address") final String address,
      @JsonProperty("topics") final List<String> topics) {
    this.address = address;
    this.topics = topics;
  }

  String address() {
    return address;
  }

  List<String> topics() {
    return topics;
  }
}
