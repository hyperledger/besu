package org.hyperledger.besu.ethereum.transaction;

public class BlockSimulationException extends RuntimeException {
  public BlockSimulationException(final String message) {
    super(message);
  }

  public BlockSimulationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
