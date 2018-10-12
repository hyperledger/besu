package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.pending;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.JsonRpcResult;

import com.fasterxml.jackson.annotation.JsonValue;

public class PendingTransactionResult implements JsonRpcResult {

  private final String hash;

  public PendingTransactionResult(final Hash hash) {
    this.hash = hash.toString();
  }

  @JsonValue
  public String getHash() {
    return hash;
  }
}
