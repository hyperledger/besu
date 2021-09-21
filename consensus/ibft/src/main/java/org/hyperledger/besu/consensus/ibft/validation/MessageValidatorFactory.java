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
package org.hyperledger.besu.consensus.ibft.validation;

import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Collection;

public class MessageValidatorFactory {

  private final ProposerSelector proposerSelector;
  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final BftExtraDataCodec bftExtraDataCodec;

  public MessageValidatorFactory(
      final ProposerSelector proposerSelector,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.proposerSelector = proposerSelector;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  private Collection<Address> getValidatorsAfterBlock(final BlockHeader parentHeader) {
    return protocolContext
        .getConsensusContext(BftContext.class)
        .getValidatorProvider()
        .getValidatorsAfterBlock(parentHeader);
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
    final BlockValidator blockValidator =
        protocolSchedule.getByBlockNumber(roundIdentifier.getSequenceNumber()).getBlockValidator();
    final Collection<Address> validators = getValidatorsAfterBlock(parentHeader);

    final BftBlockInterface bftBlockInterface =
        protocolContext.getConsensusContext(BftContext.class).getBlockInterface();

    return new MessageValidator(
        createSignedDataValidator(roundIdentifier, parentHeader),
        new ProposalBlockConsistencyValidator(),
        blockValidator,
        protocolContext,
        new RoundChangeCertificateValidator(
            validators,
            (ri) -> createSignedDataValidator(ri, parentHeader),
            roundIdentifier.getSequenceNumber(),
            bftExtraDataCodec,
            bftBlockInterface));
  }

  public RoundChangeMessageValidator createRoundChangeMessageValidator(
      final long chainHeight, final BlockHeader parentHeader) {
    final Collection<Address> validators = getValidatorsAfterBlock(parentHeader);

    final BftBlockInterface bftBlockInterface =
        protocolContext.getConsensusContext(BftContext.class).getBlockInterface();
    return new RoundChangeMessageValidator(
        new RoundChangePayloadValidator(
            (roundIdentifier) -> createSignedDataValidator(roundIdentifier, parentHeader),
            validators,
            BftHelpers.prepareMessageCountForQuorum(
                BftHelpers.calculateRequiredValidatorQuorum(validators.size())),
            chainHeight),
        new ProposalBlockConsistencyValidator(),
        bftBlockInterface);
  }

  public FutureRoundProposalMessageValidator createFutureRoundProposalMessageValidator(
      final long chainHeight, final BlockHeader parentHeader) {

    return new FutureRoundProposalMessageValidator(this, chainHeight, parentHeader);
  }
}
