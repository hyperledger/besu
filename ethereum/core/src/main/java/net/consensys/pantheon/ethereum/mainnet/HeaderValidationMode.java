package net.consensys.pantheon.ethereum.mainnet;

public enum HeaderValidationMode {
  /** No Validation. data must be pre-validated */
  NONE,

  /** Skip proof of work validation */
  LIGHT,

  /** Skip rules that can be applied when the parent is already on the blockchain */
  DETACHED_ONLY,

  /** Skip rules that can be applied before the parent is added to the block chain */
  SKIP_DETACHED,

  /** Fully validate the header */
  FULL
}
