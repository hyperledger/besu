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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.ProposerSelector;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.statemachine.TerminatedRoundArtefacts;
import tech.pegasys.pantheon.consensus.ibft.validation.RoundChangePayloadValidator.MessageValidatorForHeightFactory;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Util;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class NewRoundSignedDataValidatorTest {

  private final KeyPair proposerKey = KeyPair.generate();
  private final KeyPair validatorKey = KeyPair.generate();
  private final KeyPair otherValidatorKey = KeyPair.generate();
  private final MessageFactory proposerMessageFactory = new MessageFactory(proposerKey);
  private final MessageFactory validatorMessageFactory = new MessageFactory(validatorKey);
  private final Address proposerAddress = Util.publicKeyToAddress(proposerKey.getPublicKey());
  private final List<Address> validators = Lists.newArrayList();
  private final long chainHeight = 2;
  private final ConsensusRoundIdentifier roundIdentifier =
      new ConsensusRoundIdentifier(chainHeight, 4);
  private NewRoundPayloadValidator validator;

  private final ProposerSelector proposerSelector = mock(ProposerSelector.class);
  private final MessageValidatorForHeightFactory validatorFactory =
      mock(MessageValidatorForHeightFactory.class);
  private final SignedDataValidator signedDataValidator = mock(SignedDataValidator.class);

  private Block proposedBlock;
  private NewRound validMsg;
  private NewRoundPayload validPayload;
  private NewRoundPayload.Builder msgBuilder;

  @Before
  public void setup() {
    validators.add(Util.publicKeyToAddress(proposerKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validatorKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(otherValidatorKey.getPublicKey()));

    proposedBlock = TestHelpers.createProposalBlock(validators, roundIdentifier.getRoundNumber());
    validMsg = createValidNewRoundMessageSignedBy(proposerKey);
    validPayload = validMsg.getSignedPayload().getPayload();
    msgBuilder = NewRoundPayload.Builder.fromExisting(validMsg);

    when(proposerSelector.selectProposerForRound(any())).thenReturn(proposerAddress);

    when(validatorFactory.createAt(any())).thenReturn(signedDataValidator);
    when(signedDataValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(signedDataValidator.validatePrepareMessage(any())).thenReturn(true);

    validator =
        new NewRoundPayloadValidator(
            validators, proposerSelector, validatorFactory, 1, chainHeight);
  }

  /* NOTE: All test herein assume that the Proposer is the expected transmitter of the NewRound
   * message.
   */

  private NewRound createValidNewRoundMessageSignedBy(final KeyPair signingKey) {
    final MessageFactory messageCreator = new MessageFactory(signingKey);

    final RoundChangeCertificate.Builder builder = new RoundChangeCertificate.Builder();
    builder.appendRoundChangeMessage(
        proposerMessageFactory.createSignedRoundChangePayload(roundIdentifier, Optional.empty()));

    return messageCreator.createSignedNewRoundPayload(
        roundIdentifier,
        builder.buildCertificate(),
        messageCreator
            .createSignedProposalPayload(roundIdentifier, proposedBlock)
            .getSignedPayload());
  }

  private NewRound signPayload(final NewRoundPayload payload, final KeyPair signingKey) {

    final MessageFactory messageCreator = new MessageFactory(signingKey);

    return messageCreator.createSignedNewRoundPayload(
        payload.getRoundIdentifier(),
        payload.getRoundChangeCertificate(),
        payload.getProposalPayload());
  }

  @Test
  public void basicNewRoundMessageIsValid() {
    assertThat(validator.validateNewRoundMessage(validMsg.getSignedPayload())).isTrue();
  }

  @Test
  public void newRoundFromNonProposerFails() {
    final NewRound msg = signPayload(validPayload, validatorKey);

    assertThat(validator.validateNewRoundMessage(msg.getSignedPayload())).isFalse();
  }

  @Test
  public void newRoundTargetingRoundZeroFails() {
    msgBuilder.setRoundChangeIdentifier(
        new ConsensusRoundIdentifier(roundIdentifier.getSequenceNumber(), 0));

    final NewRound inValidMsg = signPayload(msgBuilder.build(), proposerKey);

    assertThat(validator.validateNewRoundMessage(inValidMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void newRoundTargetingDifferentSequenceNumberFails() {
    final ConsensusRoundIdentifier futureRound = TestHelpers.createFrom(roundIdentifier, 1, 0);
    msgBuilder.setRoundChangeIdentifier(futureRound);

    final NewRound inValidMsg = signPayload(msgBuilder.build(), proposerKey);

    assertThat(validator.validateNewRoundMessage(inValidMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void newRoundWithEmptyRoundChangeCertificateFails() {
    msgBuilder.setRoundChangeCertificate(new RoundChangeCertificate(Collections.emptyList()));

    final NewRound inValidMsg = signPayload(msgBuilder.build(), proposerKey);

    assertThat(validator.validateNewRoundMessage(inValidMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void newRoundWithProposalNotMatchingLatestRoundChangeFails() {
    final ConsensusRoundIdentifier preparedRound = TestHelpers.createFrom(roundIdentifier, 0, -1);
    // The previous block has been constructed with less validators, so is thus not identical
    // to the block in the new proposal (so should fail).
    final Block prevProposedBlock =
        TestHelpers.createProposalBlock(validators.subList(0, 1), roundIdentifier.getRoundNumber());

    final TerminatedRoundArtefacts mismatchedRoundArtefacts =
        new TerminatedRoundArtefacts(
            proposerMessageFactory.createSignedProposalPayload(preparedRound, prevProposedBlock),
            singletonList(
                validatorMessageFactory.createSignedPreparePayload(
                    preparedRound, prevProposedBlock.getHash())));

    msgBuilder.setRoundChangeCertificate(
        new RoundChangeCertificate(
            singletonList(
                validatorMessageFactory
                    .createSignedRoundChangePayload(
                        roundIdentifier, Optional.of(mismatchedRoundArtefacts))
                    .getSignedPayload())));

    final NewRound invalidMsg = signPayload(msgBuilder.build(), proposerKey);

    assertThat(validator.validateNewRoundMessage(invalidMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void roundChangeMessagesDoNotAllTargetRoundOfNewRoundMsgFails() {
    final ConsensusRoundIdentifier prevRound = TestHelpers.createFrom(roundIdentifier, 0, -1);

    final RoundChangeCertificate.Builder roundChangeBuilder = new RoundChangeCertificate.Builder();
    roundChangeBuilder.appendRoundChangeMessage(
        proposerMessageFactory.createSignedRoundChangePayload(roundIdentifier, Optional.empty()));
    roundChangeBuilder.appendRoundChangeMessage(
        proposerMessageFactory.createSignedRoundChangePayload(prevRound, Optional.empty()));

    msgBuilder.setRoundChangeCertificate(roundChangeBuilder.buildCertificate());

    final NewRound msg = signPayload(msgBuilder.build(), proposerKey);

    assertThat(validator.validateNewRoundMessage(msg.getSignedPayload())).isFalse();
  }

  @Test
  public void invalidEmbeddedRoundChangeMessageFails() {
    final ConsensusRoundIdentifier prevRound = TestHelpers.createFrom(roundIdentifier, 0, -1);

    final RoundChangeCertificate.Builder roundChangeBuilder = new RoundChangeCertificate.Builder();
    roundChangeBuilder.appendRoundChangeMessage(
        proposerMessageFactory.createSignedRoundChangePayload(
            roundIdentifier,
            Optional.of(
                new TerminatedRoundArtefacts(
                    proposerMessageFactory.createSignedProposalPayload(prevRound, proposedBlock),
                    Lists.newArrayList(
                        validatorMessageFactory.createSignedPreparePayload(
                            prevRound, proposedBlock.getHash()))))));

    msgBuilder.setRoundChangeCertificate(roundChangeBuilder.buildCertificate());

    // The prepare Message in the RoundChange Cert will be deemed illegal.
    when(signedDataValidator.validatePrepareMessage(any())).thenReturn(false);

    final NewRound msg = signPayload(msgBuilder.build(), proposerKey);

    assertThat(validator.validateNewRoundMessage(msg.getSignedPayload())).isFalse();
  }

  @Test
  public void lastestPreparedCertificateMatchesNewRoundProposalIsSuccessful() {
    final ConsensusRoundIdentifier latterPrepareRound =
        new ConsensusRoundIdentifier(
            roundIdentifier.getSequenceNumber(), roundIdentifier.getRoundNumber() - 1);
    final Proposal latterProposal =
        proposerMessageFactory.createSignedProposalPayload(latterPrepareRound, proposedBlock);
    final Optional<TerminatedRoundArtefacts> preparedCert =
        Optional.of(
            new TerminatedRoundArtefacts(
                latterProposal,
                Lists.newArrayList(
                    validatorMessageFactory.createSignedPreparePayload(
                        roundIdentifier, proposedBlock.getHash()))));

    // An earlier PrepareCert is added to ensure the path to find the latest PrepareCert
    // is correctly followed.
    final Block earlierBlock =
        TestHelpers.createProposalBlock(validators.subList(0, 1), roundIdentifier.getRoundNumber());
    final ConsensusRoundIdentifier earlierPreparedRound =
        new ConsensusRoundIdentifier(
            roundIdentifier.getSequenceNumber(), roundIdentifier.getRoundNumber() - 2);
    final Proposal earlierProposal =
        proposerMessageFactory.createSignedProposalPayload(earlierPreparedRound, earlierBlock);
    final Optional<TerminatedRoundArtefacts> earlierPreparedCert =
        Optional.of(
            new TerminatedRoundArtefacts(
                earlierProposal,
                Lists.newArrayList(
                    validatorMessageFactory.createSignedPreparePayload(
                        earlierPreparedRound, earlierBlock.getHash()))));

    final RoundChangeCertificate roundChangeCert =
        new RoundChangeCertificate(
            Lists.newArrayList(
                proposerMessageFactory
                    .createSignedRoundChangePayload(roundIdentifier, earlierPreparedCert)
                    .getSignedPayload(),
                validatorMessageFactory
                    .createSignedRoundChangePayload(roundIdentifier, preparedCert)
                    .getSignedPayload()));

    // Ensure a message containing the earlier proposal fails
    final NewRound newRoundWithEarlierProposal =
        proposerMessageFactory.createSignedNewRoundPayload(
            roundIdentifier, roundChangeCert, earlierProposal.getSignedPayload());
    assertThat(validator.validateNewRoundMessage(newRoundWithEarlierProposal.getSignedPayload()))
        .isFalse();

    final NewRound newRoundWithLatterProposal =
        proposerMessageFactory.createSignedNewRoundPayload(
            roundIdentifier, roundChangeCert, latterProposal.getSignedPayload());
    assertThat(validator.validateNewRoundMessage(newRoundWithLatterProposal.getSignedPayload()))
        .isTrue();
  }

  @Test
  public void embeddedProposalFailsValidation() {
    when(signedDataValidator.addSignedProposalPayload(any())).thenReturn(false, true);

    final Proposal proposal =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, proposedBlock);

    final NewRound msg =
        proposerMessageFactory.createSignedNewRoundPayload(
            roundIdentifier,
            new RoundChangeCertificate(
                Lists.newArrayList(
                    proposerMessageFactory
                        .createSignedRoundChangePayload(roundIdentifier, Optional.empty())
                        .getSignedPayload(),
                    validatorMessageFactory
                        .createSignedRoundChangePayload(roundIdentifier, Optional.empty())
                        .getSignedPayload())),
            proposal.getSignedPayload());

    assertThat(validator.validateNewRoundMessage(msg.getSignedPayload())).isFalse();
  }
}
