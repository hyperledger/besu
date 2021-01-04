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
package org.hyperledger.besu.consensus.qbft.validation;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collection;

public class MessageValidatorFactory {

  private final ProposerSelector proposerSelector;
  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;

  public MessageValidatorFactory(
      final ProposerSelector proposerSelector,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    this.proposerSelector = proposerSelector;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  private Collection<Address> getValidatorsAfterBlock(final BlockHeader parentHeader) {
    return protocolContext
        .getConsensusState(BftContext.class)
        .getVoteTallyCache()
        .getVoteTallyAfterBlock(parentHeader)
        .getValidators();
  }

  private SignedDataValidator createSignedDataValidator(
      final ConsensusRoundIdentifier roundIdentifier, final BlockHeader parentHeader) {

    return new SignedDataValidator(
        getValidatorsAfterBlock(parentHeader),
        proposerSelector.selectProposerForRound(roundIdentifier),
        roundIdentifier);
  }

  public MessageValidator createMessageValidator(
      final ConsensusRoundIdentifier roundIdentifier, final BlockHeader parentHeader) {

    final SignedDataValidator signedDataValidator =
        createSignedDataValidator(roundIdentifier, parentHeader);

    final BlockValidator blockValidator =
        protocolSchedule.getByBlockNumber(roundIdentifier.getSequenceNumber()).getBlockValidator();
    final Collection<Address> validators = getValidatorsAfterBlock(parentHeader);

    final ProposalValidator proposalValidator =
        new ProposalValidator(
            signedDataValidator,
            blockValidator,
            protocolContext,
            BftHelpers.calculateRequiredValidatorQuorum(validators.size()),
            validators);

    return new MessageValidator(signedDataValidator, proposalValidator);
  }

  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final long chainHeight, final BlockHeader parentHeader) {
    final Collection<Address> validators = getValidatorsAfterBlock(parentHeader);

    return new RoundChangeMessageValidator(
        new RoundChangePayloadValidator(validators, chainHeight),
        BftHelpers.calculateRequiredValidatorQuorum(validators.size()),
        chainHeight,
        validators);
  }

  public FutureRoundProposalMessageValidator createFutureRoundProposalMessageValidator(
      final long chainHeight, final BlockHeader parentHeader) {

    return new FutureRoundProposalMessageValidator(this, chainHeight, parentHeader);
  }
}
