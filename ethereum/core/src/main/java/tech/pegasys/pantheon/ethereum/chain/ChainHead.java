package tech.pegasys.pantheon.ethereum.chain;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.uint.UInt256;

/** Head of a blockchain. */
public final class ChainHead {

  private final Hash hash;

  private final UInt256 totalDifficulty;

  public ChainHead(final Hash hash, final UInt256 totalDifficulty) {
    this.hash = hash;
    this.totalDifficulty = totalDifficulty;
  }

  public Hash getHash() {
    return hash;
  }

  public UInt256 getTotalDifficulty() {
    return totalDifficulty;
  }
}
