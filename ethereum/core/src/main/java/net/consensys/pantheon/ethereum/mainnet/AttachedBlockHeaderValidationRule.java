package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.BlockHeader;

public interface AttachedBlockHeaderValidationRule<C> {

  /**
   * Validates a block header against its ancestors.
   *
   * @param header the block header to validate
   * @param parent the block header corresponding to the parent of the header being validated.
   * @param protocolContext the protocol context
   * @return {@code true} if valid; otherwise {@code false}
   */
  boolean validate(BlockHeader header, BlockHeader parent, ProtocolContext<C> protocolContext);
}
