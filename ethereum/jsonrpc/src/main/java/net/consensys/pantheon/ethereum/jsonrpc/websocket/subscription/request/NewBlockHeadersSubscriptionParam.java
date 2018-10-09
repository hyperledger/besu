package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class NewBlockHeadersSubscriptionParam {

  private final boolean includeTransaction;

  @JsonCreator
  NewBlockHeadersSubscriptionParam(
      @JsonProperty("includeTransactions") final boolean includeTransaction) {
    this.includeTransaction = includeTransaction;
  }

  boolean includeTransaction() {
    return includeTransaction;
  }
}
