/*
 * Copyright 2020 ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbft.core.validation;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockInterface;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.core.types.QbftValidatorProvider;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidator.SubsequentMessageValidator;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;

/** The Message validator factory. */
public class MessageValidatorFactory {

  private final ProposerSelector proposerSelector;
  private final QbftProtocolSchedule protocolSchedule;
  private final QbftValidatorProvider validatorProvider;
  private final QbftBlockInterface blockInterface;

  /**
   * Instantiates a new Message validator factory.
   *
   * @param proposerSelector the proposer selector
   * @param protocolSchedule the protocol schedule
   * @param validatorProvider the validator provider
   * @param blockInterface the block interface
   */
  public MessageValidatorFactory(
      final ProposerSelector proposerSelector,
      final QbftProtocolSchedule protocolSchedule,
      final QbftValidatorProvider validatorProvider,
      final QbftBlockInterface blockInterface) {
    this.proposerSelector = proposerSelector;
    this.protocolSchedule = protocolSchedule;
    this.validatorProvider = validatorProvider;
    this.blockInterface = blockInterface;
  }

  /**
   * Create round change message validator.
   *
   * @param chainHeight the chain height
   * @param parentHeader the parent header
   * @return the round change message validator
   */
  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final long chainHeight, final QbftBlockHeader parentHeader) {

    final Collection<Address> validatorsForHeight =
        validatorProvider.getValidatorsAfterBlock(parentHeader);

    final RoundChangePayloadValidator roundChangePayloadValidator =
        new RoundChangePayloadValidator(validatorsForHeight, chainHeight);

    return new RoundChangeMessageValidator(
        roundChangePayloadValidator,
        BftHelpers.calculateRequiredValidatorQuorum(validatorsForHeight.size()),
        chainHeight,
        validatorsForHeight,
        protocolSchedule);
  }

  /**
   * Create message validator.
   *
   * @param roundIdentifier the round identifier
   * @param parentHeader the parent header
   * @return the message validator
   */
  public MessageValidator createMessageValidator(
      final ConsensusRoundIdentifier roundIdentifier, final QbftBlockHeader parentHeader) {
    final Collection<Address> validatorsForHeight =
        validatorProvider.getValidatorsAfterBlock(parentHeader);

    final ProposalValidator proposalValidator =
        new ProposalValidator(
            blockInterface,
            protocolSchedule,
            BftHelpers.calculateRequiredValidatorQuorum(validatorsForHeight.size()),
            validatorsForHeight,
            roundIdentifier,
            proposerSelector.selectProposerForRound(roundIdentifier));

    return new MessageValidator(
        block ->
            new SubsequentMessageValidator(
                validatorsForHeight, roundIdentifier, block, blockInterface),
        proposalValidator);
  }

  /**
   * Create future round proposal message validator.
   *
   * @param chainHeight the chain height
   * @param parentHeader the parent header
   * @return the future round proposal message validator
   */
  public FutureRoundProposalMessageValidator createFutureRoundProposalMessageValidator(
      final long chainHeight, final QbftBlockHeader parentHeader) {
    return new FutureRoundProposalMessageValidator(this, chainHeight, parentHeader);
  }
}
