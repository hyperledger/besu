package net.consensys.pantheon.consensus.clique.blockcreation;

import net.consensys.pantheon.consensus.clique.VoteTallyCache;
import net.consensys.pantheon.consensus.common.ValidatorProvider;
import net.consensys.pantheon.ethereum.blockcreation.BaseBlockScheduler;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.time.Clock;

import java.util.Random;

import com.google.common.annotations.VisibleForTesting;

public class CliqueBlockScheduler extends BaseBlockScheduler {

  private final int OUT_OF_TURN_DELAY_MULTIPLIER_MILLIS = 500;

  private final VoteTallyCache voteTallyCache;
  private final Address localNodeAddress;
  private final long secondsBetweenBlocks;

  public CliqueBlockScheduler(
      final Clock clock,
      final VoteTallyCache voteTallyCache,
      final Address localNodeAddress,
      final long secondsBetweenBlocks) {
    super(clock);
    this.voteTallyCache = voteTallyCache;
    this.localNodeAddress = localNodeAddress;
    this.secondsBetweenBlocks = secondsBetweenBlocks;
  }

  @Override
  @VisibleForTesting
  public BlockCreationTimeResult getNextTimestamp(final BlockHeader parentHeader) {
    final long nextHeaderTimestamp = parentHeader.getTimestamp() + secondsBetweenBlocks;
    long milliSecondsUntilNextBlock = (nextHeaderTimestamp * 1000) - clock.millisecondsSinceEpoch();

    final CliqueProposerSelector proposerSelector = new CliqueProposerSelector(voteTallyCache);
    final Address nextSelector = proposerSelector.selectProposerForNextBlock(parentHeader);
    if (!nextSelector.equals(localNodeAddress)) {
      milliSecondsUntilNextBlock +=
          calculatorOutOfTurnDelay(voteTallyCache.getVoteTallyAtBlock(parentHeader));
    }

    return new BlockCreationTimeResult(
        nextHeaderTimestamp, Math.max(0, milliSecondsUntilNextBlock));
  }

  private int calculatorOutOfTurnDelay(final ValidatorProvider validators) {
    int countSigners = validators.getCurrentValidators().size();
    int maxDelay = ((countSigners / 2) + 1) * OUT_OF_TURN_DELAY_MULTIPLIER_MILLIS;
    Random r = new Random();
    return r.nextInt((maxDelay) + 1);
  }
}
