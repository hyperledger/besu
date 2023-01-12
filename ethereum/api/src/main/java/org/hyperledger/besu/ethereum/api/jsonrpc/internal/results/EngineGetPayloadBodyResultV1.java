package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

public class EngineGetPayloadBodyResultV1 {
  private final List<String> transactions;

  public EngineGetPayloadBodyResultV1(final List<String> transactions) {
    this.transactions = transactions;
  }

  @JsonValue
  public List<String> getTransactions() {
    return transactions;
  }
}
