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

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;

import java.util.Collection;

public class MessageValidatorFactory {

  private final ProposerSelector proposerSelector;
  private final BlockHeaderValidator<IbftContext> blockHeaderValidator;
  private final ProtocolContext<IbftContext> protocolContext;

  public MessageValidatorFactory(
      final ProposerSelector proposerSelector,
      final BlockHeaderValidator<IbftContext> blockHeaderValidator,
      final ProtocolContext<IbftContext> protocolContext) {
    this.proposerSelector = proposerSelector;
    this.blockHeaderValidator = blockHeaderValidator;
    this.protocolContext = protocolContext;
  }

  public MessageValidator createMessageValidator(
      final ConsensusRoundIdentifier roundIdentifier, final BlockHeader parentHeader) {
    return new MessageValidator(
        protocolContext.getConsensusState().getVoteTally().getValidators(),
        proposerSelector.selectProposerForRound(roundIdentifier),
        roundIdentifier,
        blockHeaderValidator,
        protocolContext,
        parentHeader);
  }

  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final BlockHeader parentHeader) {
    final Collection<Address> validators =
        protocolContext.getConsensusState().getVoteTally().getValidators();
    return new RoundChangeMessageValidator(
        roundIdentifier -> createMessageValidator(roundIdentifier, parentHeader),
        validators,
        IbftHelpers.calculateRequiredValidatorQuorum(validators.size()),
        parentHeader.getNumber() + 1);
  }

  public NewRoundMessageValidator createNewRoundValidator(final BlockHeader parentHeader) {
    final Collection<Address> validators =
        protocolContext.getConsensusState().getVoteTally().getValidators();
    return new NewRoundMessageValidator(
        validators,
        proposerSelector,
        roundIdentifier -> createMessageValidator(roundIdentifier, parentHeader),
        IbftHelpers.calculateRequiredValidatorQuorum(validators.size()),
        parentHeader.getNumber() + 1);
  }
}
