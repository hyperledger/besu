package tech.pegasys.pantheon.ethereum.development;

import tech.pegasys.pantheon.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

/**
 * This provides a difficulty calculator that can be used during development efforts; given
 * development (typically) uses CPU based mining, a negligible difficulty ensures tests etc. execute
 * quickly.
 */
public class DevelopmentDifficultyCalculators {

  public static final BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(500L);

  public static DifficultyCalculator<Void> DEVELOPER =
      (time, parent, context) -> {
        return MINIMUM_DIFFICULTY;
      };
}
