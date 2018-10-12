package tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions;

import tech.pegasys.pantheon.ethereum.core.Hash;

public class InvalidBlockException extends RuntimeException {

  public InvalidBlockException(final String message, final long blockNumber, final Hash blockHash) {
    super(message + ": " + blockNumber + ", " + blockHash);
  }
}
