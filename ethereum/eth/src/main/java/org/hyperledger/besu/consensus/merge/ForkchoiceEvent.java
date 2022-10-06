package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.datatypes.Hash;

public class ForkchoiceEvent {

  private final Hash headBlockHash;
  private final Hash safeBlockHash;
  private final Hash finalizedBlockHash;

  public ForkchoiceEvent(
      final Hash headBlockHash, final Hash safeBlockHash, final Hash finalizedBlockHash) {
    this.headBlockHash = headBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
    this.safeBlockHash = safeBlockHash;
  }

  public boolean hasValidFinalizedBlockHash() {
    return !finalizedBlockHash.equals(Hash.ZERO);
  }

  public Hash getHeadBlockHash() {
    return headBlockHash;
  }

  public Hash getFinalizedBlockHash() {
    return finalizedBlockHash;
  }

  public Hash getSafeBlockHash() {
    return safeBlockHash;
  }

  @Override
  public String toString() {
    return "ForkchoiceEvent{"
        + "headBlockHash="
        + headBlockHash
        + ", safeBlockHash="
        + safeBlockHash
        + ", finalizedBlockHash="
        + finalizedBlockHash
        + ", safeBlockHash="
        + safeBlockHash
        + '}';
  }
}
