package net.consensys.pantheon.consensus.clique.blockcreation;

import static com.google.common.base.Preconditions.checkNotNull;

import net.consensys.pantheon.consensus.clique.VoteTallyCache;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for determining which member of the validator pool should create the next block.
 *
 * <p>It does this be determining the available validators at the previous block, then selecting the
 * appropriate validator based on the chain height.
 */
public class CliqueProposerSelector {

  private final VoteTallyCache voteTallyCache;

  public CliqueProposerSelector(final VoteTallyCache voteTallyCache) {
    checkNotNull(voteTallyCache);
    this.voteTallyCache = voteTallyCache;
  }

  /**
   * Determines which validator should create the block after that supplied.
   *
   * @param parentHeader The header of the previously received block.
   * @return The address of the node which is to propose a block for the provided Round.
   */
  public Address selectProposerForNextBlock(final BlockHeader parentHeader) {

    final VoteTally parentVoteTally = voteTallyCache.getVoteTallyAtBlock(parentHeader);
    final List<Address> validatorSet = new ArrayList<>(parentVoteTally.getCurrentValidators());

    final long nextBlockNumber = parentHeader.getNumber() + 1L;
    final int indexIntoValidators = (int) (nextBlockNumber % validatorSet.size());

    return validatorSet.get(indexIntoValidators);
  }
}
