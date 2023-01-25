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
package org.hyperledger.besu.consensus.common.bft.blockcreation;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for determining which member of the validator pool should propose the next block
 * (i.e. send the Proposal message).
 *
 * <p>It does this by extracting the previous block's proposer from the ProposerSeal (stored in the
 * Blocks ExtraData) then iterating through the validator list (stored in {@link
 * ValidatorProvider}*), such that each new round for the given height is serviced by a different
 * validator.
 */
public class ProposerSelector {

  private static final Logger LOG = LoggerFactory.getLogger(ProposerSelector.class);

  private final Blockchain blockchain;

  /**
   * If set, will cause the proposer to change on successful addition of a block. Otherwise, the
   * previously successful proposer will propose the next block as well.
   */
  private final Boolean changeEachBlock;

  private final ValidatorProvider validatorProvider;

  private final BlockInterface blockInterface;

  /**
   * Instantiates a new Proposer selector.
   *
   * @param blockchain the blockchain
   * @param blockInterface the block interface
   * @param changeEachBlock the change each block
   * @param validatorProvider the validator provider
   */
  public ProposerSelector(
      final Blockchain blockchain,
      final BlockInterface blockInterface,
      final boolean changeEachBlock,
      final ValidatorProvider validatorProvider) {
    this.blockchain = blockchain;
    this.blockInterface = blockInterface;
    this.changeEachBlock = changeEachBlock;
    this.validatorProvider = validatorProvider;
  }

  /**
   * Determines which validator should be acting as the proposer for a given sequence/round.
   *
   * @param roundIdentifier Identifies the chain height and proposal attempt number.
   * @return The address of the node which is to propose a block for the provided Round.
   */
  public Address selectProposerForRound(final ConsensusRoundIdentifier roundIdentifier) {
    checkArgument(roundIdentifier.getRoundNumber() >= 0);
    checkArgument(roundIdentifier.getSequenceNumber() > 0);

    final long prevBlockNumber = roundIdentifier.getSequenceNumber() - 1;
    final Optional<BlockHeader> maybeParentHeader = blockchain.getBlockHeader(prevBlockNumber);
    if (!maybeParentHeader.isPresent()) {
      LOG.trace("Unable to determine proposer for requested block {}", prevBlockNumber);
      throw new RuntimeException("Unable to determine past proposer");
    }

    final BlockHeader blockHeader = maybeParentHeader.get();
    final Address prevBlockProposer = blockInterface.getProposerOfBlock(blockHeader);
    final Collection<Address> validatorsForRound =
        validatorProvider.getValidatorsAfterBlock(blockHeader);

    if (!validatorsForRound.contains(prevBlockProposer)) {
      return handleMissingProposer(prevBlockProposer, validatorsForRound, roundIdentifier);
    } else {
      return handleWithExistingProposer(prevBlockProposer, validatorsForRound, roundIdentifier);
    }
  }

  /**
   * If the proposer of the previous block is missing, the validator with an Address above the
   * previous will become the next validator for the first round of the next block.
   *
   * <p>And validators will change from there.
   */
  private Address handleMissingProposer(
      final Address prevBlockProposer,
      final Collection<Address> validatorsForRound,
      final ConsensusRoundIdentifier roundIdentifier) {
    final NavigableSet<Address> validatorSet = new TreeSet<>(validatorsForRound);
    final NavigableSet<Address> latterValidators = validatorSet.tailSet(prevBlockProposer, false);
    final Address nextProposer;
    if (latterValidators.isEmpty()) {
      // i.e. prevBlockProposer was at the end of the validator list, so the right validator for
      // the start of this round is the first.
      nextProposer = validatorSet.first();
    } else {
      // Else, use the first validator after the dropped entry.
      nextProposer = latterValidators.first();
    }
    return calculateRoundSpecificValidator(
        nextProposer, validatorsForRound, roundIdentifier.getRoundNumber());
  }

  /**
   * If the previous Proposer is still a validator - determine what offset should be applied for the
   * given round - factoring in a proposer change on the new block.
   */
  private Address handleWithExistingProposer(
      final Address prevBlockProposer,
      final Collection<Address> validatorsForRound,
      final ConsensusRoundIdentifier roundIdentifier) {
    int indexOffsetFromPrevBlock = roundIdentifier.getRoundNumber();
    if (changeEachBlock) {
      indexOffsetFromPrevBlock += 1;
    }
    return calculateRoundSpecificValidator(
        prevBlockProposer, validatorsForRound, indexOffsetFromPrevBlock);
  }

  /**
   * Given Round 0 of the given height should start from given proposer (baseProposer) - determine
   * which validator should be used given the indexOffset.
   */
  private Address calculateRoundSpecificValidator(
      final Address baseProposer,
      final Collection<Address> validatorsForRound,
      final int indexOffset) {
    final List<Address> currentValidatorList = new ArrayList<>(validatorsForRound);
    final int prevProposerIndex = currentValidatorList.indexOf(baseProposer);
    final int roundValidatorIndex = (prevProposerIndex + indexOffset) % currentValidatorList.size();
    return currentValidatorList.get(roundValidatorIndex);
  }
}
