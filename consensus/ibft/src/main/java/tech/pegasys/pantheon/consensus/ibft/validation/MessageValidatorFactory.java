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
package tech.pegasys.pantheon.consensus.ibft.validation;

import static tech.pegasys.pantheon.consensus.ibft.IbftHelpers.prepareMessageCountForQuorum;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;

import java.util.Collection;

public class MessageValidatorFactory {

  private final ProposerSelector proposerSelector;
  private final ProtocolContext<IbftContext> protocolContext;
  private final ProtocolSchedule<IbftContext> protocolSchedule;

  public MessageValidatorFactory(
      final ProposerSelector proposerSelector,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final ProtocolContext<IbftContext> protocolContext) {
    this.proposerSelector = proposerSelector;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  private SignedDataValidator createSignedDataValidator(
      final ConsensusRoundIdentifier roundIdentifier) {
    final BlockValidator<IbftContext> blockValidator =
        protocolSchedule.getByBlockNumber(roundIdentifier.getSequenceNumber()).getBlockValidator();

    return new SignedDataValidator(
        protocolContext.getConsensusState().getVoteTally().getValidators(),
        proposerSelector.selectProposerForRound(roundIdentifier),
        roundIdentifier,
        blockValidator,
        protocolContext);
  }

  public MessageValidator createMessageValidator(final ConsensusRoundIdentifier roundIdentifier) {
    return new MessageValidator(createSignedDataValidator(roundIdentifier));
  }

  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final BlockHeader parentHeader) {
    final Collection<Address> validators =
        protocolContext.getConsensusState().getVoteTally().getValidators();
    return new RoundChangeMessageValidator(
        new RoundChangePayloadValidator(
            this::createSignedDataValidator,
            validators,
            prepareMessageCountForQuorum(
                IbftHelpers.calculateRequiredValidatorQuorum(validators.size())),
            parentHeader.getNumber() + 1));
  }

  public NewRoundMessageValidator createNewRoundValidator(final BlockHeader parentHeader) {
    final Collection<Address> validators =
        protocolContext.getConsensusState().getVoteTally().getValidators();
    return new NewRoundMessageValidator(
        new NewRoundPayloadValidator(
            validators,
            proposerSelector,
            this::createSignedDataValidator,
            IbftHelpers.calculateRequiredValidatorQuorum(validators.size()),
            parentHeader.getNumber() + 1));
  }
}
