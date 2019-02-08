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
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.statemachine.PreparedRoundArtifacts;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.BlockValidator.BlockProcessingOutputs;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Util;
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
public class NewRoundMessageValidatorTest {

  private final NewRoundPayloadValidator payloadValidator = mock(NewRoundPayloadValidator.class);

  private final KeyPair proposerKey = KeyPair.generate();
  private final KeyPair validatorKey = KeyPair.generate();
  private final MessageFactory proposerMessageFactory = new MessageFactory(proposerKey);
  private final MessageFactory validatorMessageFactory = new MessageFactory(validatorKey);
  private final Address proposerAddress = Util.publicKeyToAddress(proposerKey.getPublicKey());
  private final Address validatorAddress = Util.publicKeyToAddress(validatorKey.getPublicKey());
  private final List<Address> validators = Lists.newArrayList(proposerAddress, validatorAddress);

  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 1);
  private final Block proposedBlock = TestHelpers.createProposalBlock(validators, roundIdentifier);

  private ProposalBlockConsistencyValidator proposalBlockConsistencyValidator =
      mock(ProposalBlockConsistencyValidator.class);

  @Mock private BlockValidator<IbftContext> blockValidator;
  private ProtocolContext<IbftContext> protocolContext;

  private NewRoundMessageValidator validator;

  @Before
  public void setup() {

    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenReturn(Optional.of(new BlockProcessingOutputs(null, null)));

    protocolContext =
        new ProtocolContext<>(
            mock(MutableBlockchain.class), mock(WorldStateArchive.class), mock(IbftContext.class));

    validator =
        new NewRoundMessageValidator(
            payloadValidator, proposalBlockConsistencyValidator, blockValidator, protocolContext);

    when(proposalBlockConsistencyValidator.validateProposalMatchesBlock(any(), any()))
        .thenReturn(true);

    when(payloadValidator.validateNewRoundMessage(any())).thenReturn(true);
  }

  @Test
  public void underlyingPayloadValidatorIsInvokedWithCorrectParameters() {
    final Proposal proposal = proposerMessageFactory.createProposal(roundIdentifier, proposedBlock);
    final NewRound message =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            new RoundChangeCertificate(emptyList()),
            proposal.getSignedPayload(),
            proposal.getBlock());

    assertThat(validator.validateNewRoundMessage(message)).isTrue();
    verify(payloadValidator, times(1)).validateNewRoundMessage(message.getSignedPayload());
  }

  @Test
  public void failedBlockValidationFailsMessageValidation() {
    final Proposal proposal = proposerMessageFactory.createProposal(roundIdentifier, proposedBlock);
    final NewRound message =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            new RoundChangeCertificate(emptyList()),
            proposal.getSignedPayload(),
            proposal.getBlock());

    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    assertThat(validator.validateNewRoundMessage(message)).isFalse();
  }

  @Test
  public void ifProposalConsistencyChecksFailsProposalIsIllegal() {
    final Proposal proposal = proposerMessageFactory.createProposal(roundIdentifier, proposedBlock);
    final NewRound message =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            new RoundChangeCertificate(emptyList()),
            proposal.getSignedPayload(),
            proposal.getBlock());

    when(proposalBlockConsistencyValidator.validateProposalMatchesBlock(any(), any()))
        .thenReturn(false);
    when(payloadValidator.validateNewRoundMessage(any())).thenReturn(true);

    assertThat(validator.validateNewRoundMessage(message)).isFalse();
    verify(proposalBlockConsistencyValidator, times(1))
        .validateProposalMatchesBlock(proposal.getSignedPayload(), proposal.getBlock());
  }

  @Test
  public void validationFailsIfUnderlyingSignedDataValidatorFails() {
    when(payloadValidator.validateNewRoundMessage(any())).thenReturn(false);
    final Proposal proposal = proposerMessageFactory.createProposal(roundIdentifier, proposedBlock);
    final NewRound message =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            new RoundChangeCertificate(emptyList()),
            proposal.getSignedPayload(),
            proposal.getBlock());

    assertThat(validator.validateNewRoundMessage(message)).isFalse();
  }

  @Test
  public void newRoundWithProposalNotMatchingLatestRoundChangeFails() {
    final ConsensusRoundIdentifier preparedRound = TestHelpers.createFrom(roundIdentifier, 0, -1);
    // The previous proposedBlock has been constructed with less validators, so is thus not
    // identical
    // to the proposedBlock in the new proposal (so should fail).
    final Block prevProposedBlock =
        TestHelpers.createProposalBlock(validators.subList(0, 1), preparedRound);

    final PreparedRoundArtifacts mismatchedRoundArtefacts =
        new PreparedRoundArtifacts(
            proposerMessageFactory.createProposal(preparedRound, prevProposedBlock),
            singletonList(
                validatorMessageFactory.createPrepare(preparedRound, prevProposedBlock.getHash())));

    final NewRound invalidMsg =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            new RoundChangeCertificate(
                singletonList(
                    validatorMessageFactory
                        .createRoundChange(roundIdentifier, Optional.of(mismatchedRoundArtefacts))
                        .getSignedPayload())),
            proposerMessageFactory
                .createProposal(roundIdentifier, proposedBlock)
                .getSignedPayload(),
            proposedBlock);

    assertThat(validator.validateNewRoundMessage(invalidMsg)).isFalse();
  }

  @Test
  public void lastestPreparedCertificateMatchesNewRoundProposalIsSuccessful() {
    final ConsensusRoundIdentifier latterPrepareRound =
        TestHelpers.createFrom(roundIdentifier, 0, -1);
    final Block latterBlock = TestHelpers.createProposalBlock(validators, latterPrepareRound);
    final Proposal latterProposal =
        proposerMessageFactory.createProposal(latterPrepareRound, latterBlock);
    final Optional<PreparedRoundArtifacts> latterTerminatedRoundArtefacts =
        Optional.of(
            new PreparedRoundArtifacts(
                latterProposal,
                Lists.newArrayList(
                    validatorMessageFactory.createPrepare(
                        latterPrepareRound, proposedBlock.getHash()))));

    // An earlier PrepareCert is added to ensure the path to find the latest PrepareCert
    // is correctly followed.
    final ConsensusRoundIdentifier earlierPreparedRound =
        new ConsensusRoundIdentifier(
            roundIdentifier.getSequenceNumber(), roundIdentifier.getRoundNumber() - 2);
    final Block earlierBlock =
        TestHelpers.createProposalBlock(validators.subList(0, 1), earlierPreparedRound);
    final Proposal earlierProposal =
        proposerMessageFactory.createProposal(earlierPreparedRound, earlierBlock);
    final Optional<PreparedRoundArtifacts> earlierTerminatedRoundArtefacts =
        Optional.of(
            new PreparedRoundArtifacts(
                earlierProposal,
                Lists.newArrayList(
                    validatorMessageFactory.createPrepare(
                        earlierPreparedRound, earlierBlock.getHash()))));

    final RoundChangeCertificate roundChangeCert =
        new RoundChangeCertificate(
            Lists.newArrayList(
                proposerMessageFactory
                    .createRoundChange(roundIdentifier, earlierTerminatedRoundArtefacts)
                    .getSignedPayload(),
                validatorMessageFactory
                    .createRoundChange(roundIdentifier, latterTerminatedRoundArtefacts)
                    .getSignedPayload()));

    // Ensure a message containing the earlier proposal fails
    final NewRound newRoundWithEarlierProposal =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            roundChangeCert,
            earlierProposal.getSignedPayload(),
            earlierProposal.getBlock());
    assertThat(validator.validateNewRoundMessage(newRoundWithEarlierProposal)).isFalse();

    final NewRound newRoundWithLatterProposal =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            roundChangeCert,
            latterProposal.getSignedPayload(),
            latterProposal.getBlock());
    assertThat(validator.validateNewRoundMessage(newRoundWithLatterProposal)).isTrue();
  }
}
