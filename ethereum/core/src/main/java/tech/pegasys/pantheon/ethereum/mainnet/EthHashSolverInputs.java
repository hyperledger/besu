package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.util.uint.UInt256;

public class EthHashSolverInputs {
  private final UInt256 target;
  private final byte[] prePowHash;
  private final long dagSeed; // typically block number

  public EthHashSolverInputs(final UInt256 target, final byte[] prePowHash, final long dagSeed) {
    this.target = target;
    this.prePowHash = prePowHash;
    this.dagSeed = dagSeed;
  }

  public UInt256 getTarget() {
    return target;
  }

  public byte[] getPrePowHash() {
    return prePowHash;
  }

  public long getDagSeed() {
    return dagSeed;
  }
}
