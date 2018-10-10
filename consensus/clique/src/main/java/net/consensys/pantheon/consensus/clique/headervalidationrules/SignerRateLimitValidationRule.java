package net.consensys.pantheon.consensus.clique.headervalidationrules;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueHelpers;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.AttachedBlockHeaderValidationRule;

public class SignerRateLimitValidationRule
    implements AttachedBlockHeaderValidationRule<CliqueContext> {

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<CliqueContext> protocolContext) {
    final Address blockSigner = CliqueHelpers.getProposerOfBlock(header);

    return CliqueHelpers.addressIsAllowedToProduceNextBlock(blockSigner, protocolContext, parent);
  }
}
