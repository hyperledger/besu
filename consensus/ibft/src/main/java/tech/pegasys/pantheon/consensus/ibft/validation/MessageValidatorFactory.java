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

  private Collection<Address> getValidatorsAfterBlock(final BlockHeader parentHeader) {
    return protocolContext
        .getConsensusState()
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
    final BlockValidator<IbftContext> blockValidator =
        protocolSchedule.getByBlockNumber(roundIdentifier.getSequenceNumber()).getBlockValidator();

    return new MessageValidator(
        createSignedDataValidator(roundIdentifier, parentHeader),
        new ProposalBlockConsistencyValidator(),
        blockValidator,
        protocolContext);
  }

  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final long chainHeight, final BlockHeader parentHeader) {
    final Collection<Address> validators = getValidatorsAfterBlock(parentHeader);

    return new RoundChangeMessageValidator(
        new RoundChangePayloadValidator(
            (roundIdentifier) -> createSignedDataValidator(roundIdentifier, parentHeader),
            validators,
            prepareMessageCountForQuorum(
                IbftHelpers.calculateRequiredValidatorQuorum(validators.size())),
            chainHeight),
        new ProposalBlockConsistencyValidator());
  }

  public NewRoundMessageValidator createNewRoundValidator(
      final long chainHeight, final BlockHeader parentHeader) {
    final Collection<Address> validators = getValidatorsAfterBlock(parentHeader);

    final BlockValidator<IbftContext> blockValidator =
        protocolSchedule.getByBlockNumber(chainHeight).getBlockValidator();

    final RoundChangeCertificateValidator roundChangeCertificateValidator =
        new RoundChangeCertificateValidator(
            validators,
            (roundIdentifier) -> createSignedDataValidator(roundIdentifier, parentHeader),
            chainHeight);

    return new NewRoundMessageValidator(
        new NewRoundPayloadValidator(
            proposerSelector,
            (roundIdentifier) -> createSignedDataValidator(roundIdentifier, parentHeader),
            chainHeight,
            roundChangeCertificateValidator),
        new ProposalBlockConsistencyValidator(),
        blockValidator,
        protocolContext,
        roundChangeCertificateValidator);
  }
}
