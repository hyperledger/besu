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
package org.hyperledger.besu.consensus.clique.blockcreation;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueMiningTracker;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.ethereum.blockcreation.AbstractMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import org.apache.logging.log4j.Logger;

public class CliqueMiningCoordinator extends AbstractMiningCoordinator<CliqueBlockMiner> {

  private static final Logger LOG = getLogger();

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
  protected void inSyncChanged(final boolean inSync) {
    synchronized (this) {
      final VoteTally validatorProvider =
          miningTracker
              .getProtocolContext()
              .getConsensusState(CliqueContext.class)
              .getVoteTallyCache()
              .getVoteTallyAfterBlock(blockchain.getChainHeadHeader());
      final boolean isValidator =
          getCoinbase()
              .filter(coinbase -> validatorProvider.getValidators().contains(coinbase))
              .isPresent();

      if (inSync && startMiningIfPossible() && isValidator) {
        LOG.info("Resuming mining operations");
      }

      if (!inSync && haltCurrentMiningOperation() && isValidator) {
        LOG.info("Pausing mining while behind chain head");
      }
    }
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
