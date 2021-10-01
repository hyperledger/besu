package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionForkChoiceUpdatedParameter {
  private final Hash headBlockHash;

  public Hash getHeadBlockHash() {
    return headBlockHash;
  }

  public Hash getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

  private final Hash finalizedBlockHash;

  @JsonCreator
  public ExecutionForkChoiceUpdatedParameter(
      @JsonProperty("headBlockHash") final Hash headBlockHash,
      @JsonProperty("finalizedBlockHash") final Hash finalizedBlockHash) {
    this.finalizedBlockHash = finalizedBlockHash;
    this.headBlockHash = headBlockHash;
  }
}
