package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.JsonRpcResult;

import com.fasterxml.jackson.annotation.JsonValue;

public class NotSynchronisingResult implements JsonRpcResult {

  @JsonValue
  public boolean getResult() {
    return false;
  }
}
