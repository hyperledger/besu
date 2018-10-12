package tech.pegasys.pantheon.consensus.common.headervalidationrules;

import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VoteValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * Responsible for ensuring the nonce is either auth or drop.
   *
   * @param header the block header to validate
   * @param parent the block header corresponding to the parent of the header being validated.
   * @return true if the nonce in the header is a valid validator vote value.
   */
  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final long nonce = header.getNonce();
    if (!VoteType.fromNonce(nonce).isPresent()) {
      LOG.trace("Nonce value ({}) is neither auth or drop.", nonce);
      return false;
    }
    return true;
  }
}
