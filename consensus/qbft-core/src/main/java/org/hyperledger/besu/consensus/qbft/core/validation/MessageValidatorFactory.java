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

import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidator.SubsequentMessageValidator;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;

/** The Message validator factory. */
public class MessageValidatorFactory {

  private final ProposerSelector proposerSelector;
  private final BftProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final BftExtraDataCodec bftExtraDataCodec;

  /**
   * Instantiates a new Message validator factory.
   *
   * @param proposerSelector the proposer selector
   * @param protocolSchedule the protocol schedule
   * @param protocolContext the protocol context
   * @param bftExtraDataCodec the bft extra data codec
   */
  public MessageValidatorFactory(
      final ProposerSelector proposerSelector,
      final BftProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.proposerSelector = proposerSelector;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  /**
   * Get the list of validators that are applicable after the given block
   *
   * @param protocolContext the protocol context
   * @param parentHeader the parent header
   * @return the list of validators
   */
  public static Collection<Address> getValidatorsAfterBlock(
      final ProtocolContext protocolContext, final BlockHeader parentHeader) {
    return protocolContext
        .getConsensusContext(BftContext.class)
        .getValidatorProvider()
        .getValidatorsAfterBlock(parentHeader);
  }

  /**
   * Get the list of validators that are applicable for the given block
   *
   * @param protocolContext the protocol context
   * @param parentHeader the parent header
   * @return the list of validators
   */
  public static Collection<Address> getValidatorsForBlock(
      final ProtocolContext protocolContext, final BlockHeader parentHeader) {
    return protocolContext
        .getConsensusContext(BftContext.class)
        .getValidatorProvider()
        .getValidatorsForBlock(parentHeader);
  }

  /**
   * Create round change message validator.
   *
   * @param chainHeight the chain height
   * @param parentHeader the parent header
   * @return the round change message validator
   */
  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final long chainHeight, final BlockHeader parentHeader) {

    final Collection<Address> validatorsForHeight =
        getValidatorsAfterBlock(protocolContext, parentHeader);

    final RoundChangePayloadValidator roundChangePayloadValidator =
        new RoundChangePayloadValidator(validatorsForHeight, chainHeight);

    return new RoundChangeMessageValidator(
        roundChangePayloadValidator,
        BftHelpers.calculateRequiredValidatorQuorum(validatorsForHeight.size()),
        chainHeight,
        validatorsForHeight,
        protocolContext,
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
      final ConsensusRoundIdentifier roundIdentifier, final BlockHeader parentHeader) {
    final Collection<Address> validatorsForHeight =
        getValidatorsAfterBlock(protocolContext, parentHeader);

    final ProposalValidator proposalValidator =
        new ProposalValidator(
            protocolContext,
            protocolSchedule,
            BftHelpers.calculateRequiredValidatorQuorum(validatorsForHeight.size()),
            validatorsForHeight,
            roundIdentifier,
            proposerSelector.selectProposerForRound(roundIdentifier),
            bftExtraDataCodec);

    final BftBlockInterface blockInterface =
        protocolContext.getConsensusContext(BftContext.class).getBlockInterface();
    return new MessageValidator(
        block ->
            new SubsequentMessageValidator(
                validatorsForHeight, roundIdentifier, block, blockInterface, bftExtraDataCodec),
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
      final long chainHeight, final BlockHeader parentHeader) {
    return new FutureRoundProposalMessageValidator(this, chainHeight, parentHeader);
  }
}
