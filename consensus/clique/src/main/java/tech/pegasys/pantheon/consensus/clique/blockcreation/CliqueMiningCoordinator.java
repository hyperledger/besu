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
package tech.pegasys.pantheon.consensus.clique.blockcreation;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueMiningTracker;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;

public class CliqueMiningCoordinator
    extends AbstractMiningCoordinator<CliqueContext, CliqueBlockMiner> {

  private final CliqueMiningTracker miningTracker;

  public CliqueMiningCoordinator(
      final Blockchain blockchain,
      final CliqueMinerExecutor executor,
      final SyncState syncState,
      final CliqueMiningTracker miningTracker) {
    super(blockchain, executor, syncState);
    this.miningTracker = miningTracker;
  }

  @Override
  protected boolean newChainHeadInvalidatesMiningOperation(final BlockHeader newChainHeadHeader) {
    if (currentRunningMiner.isEmpty()) {
      return true;
    }

    if (miningTracker.blockCreatedLocally(newChainHeadHeader)) {
      return true;
    }

    return networkBlockBetterThanCurrentMiner(newChainHeadHeader);
  }

  private boolean networkBlockBetterThanCurrentMiner(final BlockHeader newChainHeadHeader) {
    final BlockHeader parentHeader = currentRunningMiner.get().getParentHeader();
    final long currentMinerTargetHeight = parentHeader.getNumber() + 1;
    if (currentMinerTargetHeight < newChainHeadHeader.getNumber()) {
      return true;
    }

    final boolean nodeIsMining = miningTracker.canMakeBlockNextRound(parentHeader);
    final boolean nodeIsInTurn = miningTracker.isProposerAfter(parentHeader);

    return !nodeIsMining || !nodeIsInTurn;
  }
}
