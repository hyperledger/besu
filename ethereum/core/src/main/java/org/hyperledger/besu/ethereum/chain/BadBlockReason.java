package org.hyperledger.besu.ethereum.chain;

public enum BadBlockReason {
  // Standard spec-related validation failures
  SPEC_VALIDATION_FAILURE,
  // When an unexpected exception occurs during block processing
  EXCEPTIONAL_BLOCK_PROCESSING,
  // This block is bad because it descends from a bad block
  DESCENDS_FROM_BAD_BLOCK,
}
