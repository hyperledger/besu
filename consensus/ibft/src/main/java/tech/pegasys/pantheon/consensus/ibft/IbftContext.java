package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;

/** Holds the IBFT specific mutable state. */
public class IbftContext {

  private final VoteTally voteTally;
  private final VoteProposer voteProposer;

  public IbftContext(final VoteTally voteTally, final VoteProposer voteProposer) {
    this.voteTally = voteTally;
    this.voteProposer = voteProposer;
  }

  public VoteTally getVoteTally() {
    return voteTally;
  }

  public VoteProposer getVoteProposer() {
    return voteProposer;
  }
}
