/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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
