package net.consensys.pantheon.consensus.clique.headervalidationrules;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueDifficultyCalculator;
import net.consensys.pantheon.consensus.clique.CliqueHelpers;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.AttachedBlockHeaderValidationRule;

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
