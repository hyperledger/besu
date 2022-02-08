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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.ProposedBlockHelpers;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangeCertificate;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MessageValidatorTest {

  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final MessageFactory messageFactory = new MessageFactory(nodeKey);
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);

  private final SignedDataValidator signedDataValidator = mock(SignedDataValidator.class);
  private final ProposalBlockConsistencyValidator proposalBlockConsistencyValidator =
      mock(ProposalBlockConsistencyValidator.class);

  @Mock private BlockValidator blockValidator;
  private ProtocolContext protocolContext;
  private final RoundChangeCertificateValidator roundChangeCertificateValidator =
      mock(RoundChangeCertificateValidator.class);

  private MessageValidator messageValidator;

  private final List<Address> validators =
      Lists.newArrayList(
          AddressHelpers.ofValue(0),
          AddressHelpers.ofValue(1),
          AddressHelpers.ofValue(2),
          AddressHelpers.ofValue(3));

  private final Block block = ProposedBlockHelpers.createProposalBlock(validators, roundIdentifier);

  @Before
  public void setup() {
    when(signedDataValidator.validateProposal(any())).thenReturn(true);
    when(signedDataValidator.validatePrepare(any())).thenReturn(true);
    when(signedDataValidator.validateCommit(any())).thenReturn(true);

    when(proposalBlockConsistencyValidator.validateProposalMatchesBlock(any(), any(), any()))
        .thenReturn(true);

    BftContext mockBftCtx = mock(BftContext.class);
    when(mockBftCtx.as(Mockito.any())).thenReturn(mockBftCtx);

    protocolContext =
        new ProtocolContext(
            mock(MutableBlockchain.class), mock(WorldStateArchive.class), mockBftCtx);

    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));

    when(roundChangeCertificateValidator.validateProposalMessageMatchesLatestPrepareCertificate(
            any(), any()))
        .thenReturn(true);
    when(roundChangeCertificateValidator.validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
            any(), any()))
        .thenReturn(true);

    messageValidator =
        new MessageValidator(
            signedDataValidator,
            proposalBlockConsistencyValidator,
            blockValidator,
            protocolContext,
            roundChangeCertificateValidator);
  }

  @Test
  public void messageValidatorDefersToUnderlyingSignedDataValidator() {
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, Optional.empty());

    final Prepare prepare = messageFactory.createPrepare(roundIdentifier, block.getHash());

    final Commit commit =
        messageFactory.createCommit(
            roundIdentifier, block.getHash(), nodeKey.sign(block.getHash()));

    assertThat(messageValidator.validateProposal(proposal)).isTrue();
    verify(signedDataValidator, times(1)).validateProposal(proposal.getSignedPayload());

    messageValidator.validatePrepare(prepare);
    verify(signedDataValidator, times(1)).validatePrepare(prepare.getSignedPayload());

    messageValidator.validateCommit(commit);
    verify(signedDataValidator, times(1)).validateCommit(commit.getSignedPayload());
  }

  @Test
  public void ifProposalConsistencyChecksFailProposalIsIllegal() {
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, Optional.empty());
    when(proposalBlockConsistencyValidator.validateProposalMatchesBlock(any(), any(), any()))
        .thenReturn(false);

    assertThat(messageValidator.validateProposal(proposal)).isFalse();
    verify(proposalBlockConsistencyValidator, times(1))
        .validateProposalMatchesBlock(
            eq(proposal.getSignedPayload()), eq(proposal.getBlock()), any());
  }

  @Test
  public void blockValidationFailureFailsValidation() {
    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenReturn(new Result("Failed"));

    final Proposal proposalMsg =
        messageFactory.createProposal(roundIdentifier, block, Optional.empty());

    assertThat(messageValidator.validateProposal(proposalMsg)).isFalse();
  }

  @Test
  public void proposalFailsValidationIfRoundChangeCertificateDoeNotMatchBlock() {
    final ConsensusRoundIdentifier nonZeroRound = new ConsensusRoundIdentifier(1, 1);
    when(roundChangeCertificateValidator.validateProposalMessageMatchesLatestPrepareCertificate(
            any(), any()))
        .thenReturn(false);

    final Proposal proposal =
        messageFactory.createProposal(
            nonZeroRound, block, Optional.of(new RoundChangeCertificate(emptyList())));

    assertThat(messageValidator.validateProposal(proposal)).isFalse();
  }

  @Test
  public void proposalFailsValidationIfRoundChangeIsNotSelfConsistent() {
    final ConsensusRoundIdentifier nonZeroRound = new ConsensusRoundIdentifier(1, 1);
    when(roundChangeCertificateValidator.validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
            any(), any()))
        .thenReturn(false);

    final Proposal proposal =
        messageFactory.createProposal(
            nonZeroRound, block, Optional.of(new RoundChangeCertificate(emptyList())));

    assertThat(messageValidator.validateProposal(proposal)).isFalse();
  }

  @Test
  public void proposalForRoundZeroFailsIfItContainsARoundChangeCertificate() {
    final Proposal proposal =
        messageFactory.createProposal(
            roundIdentifier, block, Optional.of(new RoundChangeCertificate(emptyList())));

    assertThat(messageValidator.validateProposal(proposal)).isFalse();
    verify(roundChangeCertificateValidator, never())
        .validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(any(), any());
    verify(roundChangeCertificateValidator, never())
        .validateProposalMessageMatchesLatestPrepareCertificate(any(), any());
  }

  @Test
  public void proposalForRoundsGreaterThanZeroFailIfNoRoundChangeCertificateAvailable() {
    final ConsensusRoundIdentifier nonZeroRound = new ConsensusRoundIdentifier(1, 1);
    final Proposal proposal = messageFactory.createProposal(nonZeroRound, block, Optional.empty());

    assertThat(messageValidator.validateProposal(proposal)).isFalse();
    verify(roundChangeCertificateValidator, never())
        .validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(any(), any());
    verify(roundChangeCertificateValidator, never())
        .validateProposalMessageMatchesLatestPrepareCertificate(any(), any());
  }
}
