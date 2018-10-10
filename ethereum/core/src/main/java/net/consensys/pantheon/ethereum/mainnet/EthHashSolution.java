package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.Hash;

public class EthHashSolution {
  private final long nonce;
  private final Hash mixHash;
  private final byte[] powHash;

  public EthHashSolution(final long nonce, final Hash mixHash, final byte[] powHash) {
    this.nonce = nonce;
    this.mixHash = mixHash;
    this.powHash = powHash;
  }

  public long getNonce() {
    return nonce;
  }

  public Hash getMixHash() {
    return mixHash;
  }

  public byte[] getPowHash() {
    return powHash;
  }
}
