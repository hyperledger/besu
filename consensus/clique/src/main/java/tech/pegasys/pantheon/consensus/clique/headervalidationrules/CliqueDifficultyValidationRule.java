package tech.pegasys.pantheon.consensus.clique.headervalidationrules;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueDifficultyCalculator;
import tech.pegasys.pantheon.consensus.clique.CliqueHelpers;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import java.math.BigInteger;

public class CliqueDifficultyValidationRule
    implements AttachedBlockHeaderValidationRule<CliqueContext> {

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<CliqueContext> protocolContext) {
    final Address actualBlockCreator = CliqueHelpers.getProposerOfBlock(header);

    final CliqueDifficultyCalculator diffCalculator =
        new CliqueDifficultyCalculator(actualBlockCreator);
    final BigInteger expectedDifficulty = diffCalculator.nextDifficulty(0, parent, protocolContext);

    final BigInteger actualDifficulty =
        new BigInteger(1, header.getDifficulty().getBytes().extractArray());

    return expectedDifficulty.equals(actualDifficulty);
  }
}
