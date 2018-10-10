package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures the hash of the parent block matches that specified in the parent hash of the proposed
 * header.
 */
public class AncestryValidationRule implements DetachedBlockHeaderValidationRule {
  private final Logger LOGGER = LogManager.getLogger(AncestryValidationRule.class);

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (!header.getParentHash().equals(parent.getHash())) {
      LOGGER.trace(
          "Invalid parent block header.  Parent hash {} does not match "
              + "supplied parent header {}.",
          header.getParentHash(),
          parent.getHash());
      return false;
    }

    if (header.getNumber() != (parent.getNumber() + 1)) {
      LOGGER.trace(
          "Invalid block header: number {} is not one more than parent number {}",
          header.getNumber(),
          parent.getNumber());
      return false;
    }

    return true;
  }
}
