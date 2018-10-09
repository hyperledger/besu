package net.consensys.pantheon.ethereum.eth.sync.tasks.exceptions;

import net.consensys.pantheon.ethereum.core.Hash;

public class InvalidBlockException extends RuntimeException {

  public InvalidBlockException(final String message, final long blockNumber, final Hash blockHash) {
    super(message + ": " + blockNumber + ", " + blockHash);
  }
}
