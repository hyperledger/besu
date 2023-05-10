package org.hyperledger.besu.ethereum.linea;

public record CalldataLimits(int transactionMaxSize, int blockMaxSize) {
  public static final CalldataLimits NO_LIMITS = new CalldataLimits(-1, -1);
}
