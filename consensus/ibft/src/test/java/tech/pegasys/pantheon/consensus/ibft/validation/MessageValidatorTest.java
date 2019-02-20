/*
 * Copyright 2019 ConsenSys AG.
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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.BlockValidator.BlockProcessingOutputs;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MessageValidatorTest {

  private KeyPair keyPair = KeyPair.generate();
  private MessageFactory messageFactory = new MessageFactory(keyPair);
  private ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);

  private SignedDataValidator signedDataValidator = mock(SignedDataValidator.class);
  private ProposalBlockConsistencyValidator proposalBlockConsistencyValidator =
      mock(ProposalBlockConsistencyValidator.class);

  @Mock private BlockValidator<IbftContext> blockValidator;
  private ProtocolContext<IbftContext> protocolContext;
  private final RoundChangeCertificateValidator roundChangeCertificateValidator =
      mock(RoundChangeCertificateValidator.class);

  private MessageValidator messageValidator;

  private final List<Address> validators =
      Lists.newArrayList(
          AddressHelpers.ofValue(0),
          AddressHelpers.ofValue(1),
          AddressHelpers.ofValue(2),
          AddressHelpers.ofValue(3));

  private final Block block = TestHelpers.createProposalBlock(validators, roundIdentifier);

  @Before
  public void setup() {
    when(signedDataValidator.validateProposal(any())).thenReturn(true);
    when(signedDataValidator.validatePrepare(any())).thenReturn(true);
    when(signedDataValidator.validateCommit(any())).thenReturn(true);

    when(proposalBlockConsistencyValidator.validateProposalMatchesBlock(any(), any()))
        .thenReturn(true);

    protocolContext =
        new ProtocolContext<>(
            mock(MutableBlockchain.class), mock(WorldStateArchive.class), mock(IbftContext.class));

    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenReturn(Optional.of(new BlockProcessingOutputs(null, null)));

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
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), keyPair));

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
    when(proposalBlockConsistencyValidator.validateProposalMatchesBlock(any(), any()))
        .thenReturn(false);

    assertThat(messageValidator.validateProposal(proposal)).isFalse();
    verify(proposalBlockConsistencyValidator, times(1))
        .validateProposalMatchesBlock(proposal.getSignedPayload(), proposal.getBlock());
  }

  @Test
  public void blockValidationFailureFailsValidation() {
    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenReturn(Optional.empty());

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
