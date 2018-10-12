package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;

public interface DetachedBlockHeaderValidationRule {

  /**
   * Validates a block header against its parent.
   *
   * @param header the block header to validate
   * @param parent the block header corresponding to the parent of the header being validated.
   * @return {@code true} if valid; otherwise {@code false}
   */
  boolean validate(BlockHeader header, BlockHeader parent);

  default boolean includeInLightValidation() {
    return true;
  }
}
