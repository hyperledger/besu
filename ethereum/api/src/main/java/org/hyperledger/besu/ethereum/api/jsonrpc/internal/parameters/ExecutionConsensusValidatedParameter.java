package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.ConsensusStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionConsensusValidatedParameter {
  private final Hash blockHash;
  private final ConsensusStatus status;

  @JsonCreator
  public ExecutionConsensusValidatedParameter(
      @JsonProperty("blockHash") final Hash blockHash,
      @JsonProperty("status") final ConsensusStatus status) {
    this.blockHash = blockHash;
    this.status = status;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public ConsensusStatus getStatus() {
    return status;
  }
}
