package tech.pegasys.pantheon.consensus.clique.headervalidationrules;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueHelpers;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.AttachedBlockHeaderValidationRule;

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
