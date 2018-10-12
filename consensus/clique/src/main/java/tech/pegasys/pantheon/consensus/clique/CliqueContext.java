package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.consensus.common.VoteProposer;

/**
 * Holds the data which lives "in parallel" with the importation of blocks etc. when using the
 * Clique consensus mechanism.
 */
public class CliqueContext {

  private final VoteTallyCache voteTallyCache;
  private final VoteProposer voteProposer;

  public CliqueContext(final VoteTallyCache voteTallyCache, final VoteProposer voteProposer) {
    this.voteTallyCache = voteTallyCache;
    this.voteProposer = voteProposer;
  }

  public VoteTallyCache getVoteTallyCache() {
    return voteTallyCache;
  }

  public VoteProposer getVoteProposer() {
    return voteProposer;
  }
}
