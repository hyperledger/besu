package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import net.consensys.pantheon.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CalculatedDifficultyValidationRule<C> implements AttachedBlockHeaderValidationRule<C> {
  private final Logger LOG = LogManager.getLogger(CalculatedDifficultyValidationRule.class);
  private final DifficultyCalculator<C> difficultyCalculator;

  public CalculatedDifficultyValidationRule(final DifficultyCalculator<C> difficultyCalculator) {
    this.difficultyCalculator = difficultyCalculator;
  }

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext<C> context) {
    final BigInteger actualDifficulty =
        new BigInteger(1, header.getDifficulty().getBytes().extractArray());
    final BigInteger expectedDifficulty =
        difficultyCalculator.nextDifficulty(header.getTimestamp(), parent, context);

    if (actualDifficulty.compareTo(expectedDifficulty) != 0) {
      LOG.trace(
          "Invalid block header: difficulty {} does not equal expected difficulty {}",
          actualDifficulty,
          expectedDifficulty);
      return false;
    }

    return true;
  }
}
