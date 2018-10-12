package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonValue;

public class TransactionHashResult implements TransactionResult {

  private final String hash;

  public TransactionHashResult(final String hash) {
    this.hash = hash;
  }

  @JsonValue
  public String getHash() {
    return hash;
  }
}
