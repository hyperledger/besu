package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates the gas used in executing the block (defined by the supplied header) is less than or
 * equal to the current gas limit.
 */
public class GasUsageValidationRule implements DetachedBlockHeaderValidationRule {

  private final Logger LOG = LogManager.getLogger(GasUsageValidationRule.class);

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    if (header.getGasUsed() > header.getGasLimit()) {
      LOG.trace(
          "Invalid block header: gas used {} exceeds gas limit {}",
          header.getGasUsed(),
          header.getGasLimit());
      return false;
    }

    return true;
  }
}
