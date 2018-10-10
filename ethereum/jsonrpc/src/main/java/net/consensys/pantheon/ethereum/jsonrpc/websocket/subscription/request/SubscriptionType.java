package net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SubscriptionType {
  @JsonProperty("newHeads")
  NEW_BLOCK_HEADERS("newHeads"),

  @JsonProperty("logs")
  LOGS("logs"),

  @JsonProperty("newPendingTransactions")
  NEW_PENDING_TRANSACTIONS("newPendingTransactions"),

  @JsonProperty("syncing")
  SYNCING("syncing");

  private final String code;

  SubscriptionType(final String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static SubscriptionType fromCode(final String code) {
    for (final SubscriptionType subscriptionType : SubscriptionType.values()) {
      if (code.equals(subscriptionType.getCode())) {
        return subscriptionType;
      }
    }

    throw new IllegalArgumentException(String.format("Invalid subscription type '%s'", code));
  }
}
