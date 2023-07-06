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
package org.hyperledger.besu.consensus.common.validator.blockbased;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the logic to extract vote tally state from the blockchain and update it as blocks are
 * added.
 */
class VoteTallyUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(VoteTallyUpdater.class);

  private final EpochManager epochManager;
  private final BlockInterface blockInterface;

  VoteTallyUpdater(final EpochManager epochManager, final BlockInterface blockInterface) {
    this.epochManager = epochManager;
    this.blockInterface = blockInterface;
  }

  /**
   * Create a new VoteTally based on the current blockchain state.
   *
   * @param blockchain the blockchain to load the current state from
   * @return a VoteTally reflecting the state of the blockchain head
   */
  VoteTally buildVoteTallyFromBlockchain(final Blockchain blockchain) {
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    final long epochBlockNumber = epochManager.getLastEpochBlock(chainHeadBlockNumber);
    LOG.info("Loading validator voting state starting from block {}", epochBlockNumber);
    final BlockHeader epochBlock = blockchain.getBlockHeader(epochBlockNumber).get();
    final Collection<Address> initialValidators = blockInterface.validatorsInBlock(epochBlock);
    final VoteTally voteTally = new VoteTally(initialValidators);
    for (long blockNumber = epochBlockNumber + 1;
        blockNumber <= chainHeadBlockNumber;
        blockNumber++) {
      updateForBlock(blockchain.getBlockHeader(blockNumber).get(), voteTally);
    }
    return voteTally;
  }

  /**
   * Update the vote tally to reflect changes caused by appending a new block to the chain.
   *
   * @param header the header of the block being added
   * @param voteTally the vote tally to update
   */
  void updateForBlock(final BlockHeader header, final VoteTally voteTally) {
    if (epochManager.isEpochBlock(header.getNumber())) {
      voteTally.discardOutstandingVotes();
      return;
    }
    final Optional<ValidatorVote> vote = blockInterface.extractVoteFromHeader(header);
    vote.ifPresent(voteTally::addVote);
  }
}
