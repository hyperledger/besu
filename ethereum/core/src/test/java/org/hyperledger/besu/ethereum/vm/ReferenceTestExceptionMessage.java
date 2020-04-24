package org.hyperledger.besu.ethereum.vm;

public enum ReferenceTestExceptionMessage {
  INVALID_GENESIS_RLP("genesisRLP in test != genesisRLP on remote client! (%s' != '%s'"),
  INVALID_LAST_BLOCK_HASH("lastblockhash does not match! remote: %s, test: %s");

  final String message;

  ReferenceTestExceptionMessage(final String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
