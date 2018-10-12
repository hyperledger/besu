package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import net.consensys.pantheon.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for ensuring the extra data fields in the header contain the appropriate number of
 * bytes.
 */
public class ExtraDataMaxLengthValidationRule implements DetachedBlockHeaderValidationRule {

  private final Logger LOG = LogManager.getLogger(ExtraDataMaxLengthValidationRule.class);
  private final long maxExtraDataBytes;

  public ExtraDataMaxLengthValidationRule(final long maxExtraDataBytes) {
    this.maxExtraDataBytes = maxExtraDataBytes;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    return validateExtraData(header.getExtraData());
  }

  private boolean validateExtraData(final BytesValue extraData) {
    if (extraData.size() > maxExtraDataBytes) {
      LOG.trace(
          "Invalid block header: extra data field length {} is greater {}",
          extraData.size(),
          maxExtraDataBytes);
      return false;
    }

    return true;
  }
}
