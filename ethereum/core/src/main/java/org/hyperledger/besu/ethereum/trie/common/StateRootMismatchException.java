package org.hyperledger.besu.ethereum.trie.common;

import org.hyperledger.besu.datatypes.Hash;

public class StateRootMismatchException extends RuntimeException {

  private final Hash actualRoot;
  private final Hash expectedRoot;

  public StateRootMismatchException(final Hash expectedRoot, final Hash actualRoot) {
    this.expectedRoot = expectedRoot;
    this.actualRoot = actualRoot;
  }

  @Override
  public String getMessage() {
    return
        "World State Root does not match expected value, header "
            + expectedRoot.toHexString()
            + " calculated "
            + actualRoot.toHexString();
  }

  public Hash getActualRoot() {
    return actualRoot;
  }

  public Hash getExpectedRoot() {
    return expectedRoot;
  }
}
