package net.consensys.pantheon.consensus.clique;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DifficultyCalculator;

import java.math.BigInteger;

public class CliqueDifficultyCalculator implements DifficultyCalculator<CliqueContext> {

  private final Address localAddress;

  private final BigInteger IN_TURN_DIFFICULTY = BigInteger.valueOf(2);
  private final BigInteger OUT_OF_TURN_DIFFICULTY = BigInteger.ONE;

  public CliqueDifficultyCalculator(final Address localAddress) {
    this.localAddress = localAddress;
  }

  @Override
  public BigInteger nextDifficulty(
      final long time, final BlockHeader parent, final ProtocolContext<CliqueContext> context) {

    final Address nextProposer =
        CliqueHelpers.getProposerForBlockAfter(
            parent, context.getConsensusState().getVoteTallyCache());
    return nextProposer.equals(localAddress) ? IN_TURN_DIFFICULTY : OUT_OF_TURN_DIFFICULTY;
  }
}
