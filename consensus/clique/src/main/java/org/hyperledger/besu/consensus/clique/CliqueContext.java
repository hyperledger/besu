/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.PoAContext;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTallyCache;

/**
 * Holds the data which lives "in parallel" with the importation of blocks etc. when using the
 * Clique consensus mechanism.
 */
public class CliqueContext implements PoAContext {

  private final VoteTallyCache voteTallyCache;
  private final VoteProposer voteProposer;
  private final EpochManager epochManager;
  private final BlockInterface blockInterface;

  public CliqueContext(
      final VoteTallyCache voteTallyCache,
      final VoteProposer voteProposer,
      final EpochManager epochManager,
      final BlockInterface blockInterface) {
    this.voteTallyCache = voteTallyCache;
    this.voteProposer = voteProposer;
    this.epochManager = epochManager;
    this.blockInterface = blockInterface;
  }

  public VoteTallyCache getVoteTallyCache() {
    return voteTallyCache;
  }

  public VoteProposer getVoteProposer() {
    return voteProposer;
  }

  public EpochManager getEpochManager() {
    return epochManager;
  }

  @Override
  public BlockInterface getBlockInterface() {
    return blockInterface;
  }
}
