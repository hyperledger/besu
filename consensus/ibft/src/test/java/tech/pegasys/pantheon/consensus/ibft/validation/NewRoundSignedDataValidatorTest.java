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
import tech.pegasys.pantheon.consensus.ibft.validation.RoundChangePayloadValidator.MessageValidatorForHeightFactory;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Util;

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
  private final RoundChangeCertificateValidator roundChangeCertificateValidator =
      mock(RoundChangeCertificateValidator.class);

  private Block proposedBlock;
  private NewRound validMsg;
  private NewRoundPayload validPayload;
  private NewRoundPayload.Builder msgBuilder;

  @Before
  public void setup() {
    validators.add(Util.publicKeyToAddress(proposerKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validatorKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(otherValidatorKey.getPublicKey()));

    proposedBlock = TestHelpers.createProposalBlock(validators, roundIdentifier);
    validMsg = createValidNewRoundMessageSignedBy(proposerKey);
    validPayload = validMsg.getSignedPayload().getPayload();
    msgBuilder = NewRoundPayload.Builder.fromExisting(validMsg);

    when(proposerSelector.selectProposerForRound(any())).thenReturn(proposerAddress);

    when(validatorFactory.createAt(any())).thenReturn(signedDataValidator);
    when(signedDataValidator.validateProposal(any())).thenReturn(true);
    when(signedDataValidator.validatePrepare(any())).thenReturn(true);

    when(roundChangeCertificateValidator.validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
            any(), any()))
        .thenReturn(true);

    validator =
        new NewRoundPayloadValidator(
            proposerSelector, validatorFactory, chainHeight, roundChangeCertificateValidator);
  }

  /* NOTE: All test herein assume that the Proposer is the expected transmitter of the NewRound
   * message.
   */

  private NewRound createValidNewRoundMessageSignedBy(final KeyPair signingKey) {
    final MessageFactory messageCreator = new MessageFactory(signingKey);

    final RoundChangeCertificate.Builder builder = new RoundChangeCertificate.Builder();
    builder.appendRoundChangeMessage(
        proposerMessageFactory.createRoundChange(roundIdentifier, Optional.empty()));

    return messageCreator.createNewRound(
        roundIdentifier,
        builder.buildCertificate(),
        messageCreator.createProposal(roundIdentifier, proposedBlock).getSignedPayload(),
        proposedBlock);
  }

  private NewRound signPayload(
      final NewRoundPayload payload, final KeyPair signingKey, final Block block) {

    final MessageFactory messageCreator = new MessageFactory(signingKey);

    return messageCreator.createNewRound(
        payload.getRoundIdentifier(),
        payload.getRoundChangeCertificate(),
        payload.getProposalPayload(),
        block);
  }

  @Test
  public void basicNewRoundMessageIsValid() {
    assertThat(validator.validateNewRoundMessage(validMsg.getSignedPayload())).isTrue();
  }

  @Test
  public void newRoundFromNonProposerFails() {
    final NewRound msg = signPayload(validPayload, validatorKey, proposedBlock);

    assertThat(validator.validateNewRoundMessage(msg.getSignedPayload())).isFalse();
  }

  @Test
  public void newRoundTargetingRoundZeroFails() {
    msgBuilder.setRoundChangeIdentifier(
        new ConsensusRoundIdentifier(roundIdentifier.getSequenceNumber(), 0));

    final NewRound inValidMsg = signPayload(msgBuilder.build(), proposerKey, proposedBlock);

    assertThat(validator.validateNewRoundMessage(inValidMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void newRoundTargetingDifferentSequenceNumberFails() {
    final ConsensusRoundIdentifier futureRound = TestHelpers.createFrom(roundIdentifier, 1, 0);
    msgBuilder.setRoundChangeIdentifier(futureRound);

    final NewRound inValidMsg = signPayload(msgBuilder.build(), proposerKey, proposedBlock);

    assertThat(validator.validateNewRoundMessage(inValidMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void roundChangeCertificateFailsValidation() {
    when(roundChangeCertificateValidator.validateRoundChangeMessagesAndEnsureTargetRoundMatchesRoot(
            any(), any()))
        .thenReturn(false);
    assertThat(validator.validateNewRoundMessage(validMsg.getSignedPayload())).isFalse();
  }

  @Test
  public void embeddedProposalFailsValidation() {
    when(signedDataValidator.validateProposal(any())).thenReturn(false, true);

    final Proposal proposal = proposerMessageFactory.createProposal(roundIdentifier, proposedBlock);

    final NewRound msg =
        proposerMessageFactory.createNewRound(
            roundIdentifier,
            new RoundChangeCertificate(
                Lists.newArrayList(
                    proposerMessageFactory
                        .createRoundChange(roundIdentifier, Optional.empty())
                        .getSignedPayload(),
                    validatorMessageFactory
                        .createRoundChange(roundIdentifier, Optional.empty())
                        .getSignedPayload())),
            proposal.getSignedPayload(),
            proposedBlock);

    assertThat(validator.validateNewRoundMessage(msg.getSignedPayload())).isFalse();
  }
}
