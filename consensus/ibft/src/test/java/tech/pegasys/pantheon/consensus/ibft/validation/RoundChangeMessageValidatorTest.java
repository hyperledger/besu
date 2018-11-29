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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftMessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftPreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedRoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.validation.RoundChangeMessageValidator.MessageValidatorFactory;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class RoundChangeMessageValidatorTest {

  private final KeyPair proposerKey = KeyPair.generate();
  private final KeyPair validatorKey = KeyPair.generate();
  private final KeyPair nonValidatorKey = KeyPair.generate();
  private final IbftMessageFactory proposerMessageFactory = new IbftMessageFactory(proposerKey);
  private final IbftMessageFactory validatorMessageFactory = new IbftMessageFactory(validatorKey);
  private final IbftMessageFactory nonValidatorMessageFactory =
      new IbftMessageFactory(nonValidatorKey);

  private final ConsensusRoundIdentifier currentRound = new ConsensusRoundIdentifier(2, 3);
  private final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(2, 4);

  private final Block block = mock(Block.class);

  private final MessageValidator basicValidator = mock(MessageValidator.class);
  private final List<Address> validators = Lists.newArrayList();

  private final MessageValidatorFactory validatorFactory = mock(MessageValidatorFactory.class);
  private final RoundChangeMessageValidator validator =
      new RoundChangeMessageValidator(validatorFactory, validators, 1, currentRound);

  @Before
  public void setup() {
    validators.add(Util.publicKeyToAddress(proposerKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validatorKey.getPublicKey()));

    when(block.getHash()).thenReturn(Hash.fromHexStringLenient("1"));
    when(validatorFactory.createAt(any())).thenReturn(basicValidator);

    // By default, have all basic messages being valid thus any failures are attributed to logic
    // in the RoundChangeMessageValidator
    when(basicValidator.addPreprepareMessage(any())).thenReturn(true);
    when(basicValidator.validatePrepareMessage(any())).thenReturn(true);
  }

  @Test
  public void roundChangeSentByNonValidatorFails() {
    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        nonValidatorMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.empty());
    assertThat(validator.validateMessage(msg)).isFalse();
  }

  @Test
  public void roundChangeContainingNoCertificateIsSuccessful() {
    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.empty());

    assertThat(validator.validateMessage(msg)).isTrue();
  }

  @Test
  public void roundChangeContainingInvalidPreprepareFails() {
    final IbftPreparedCertificate prepareCertificate =
        new IbftPreparedCertificate(
            proposerMessageFactory.createIbftSignedPrePrepareMessageData(currentRound, block),
            Collections.emptyList());

    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.of(prepareCertificate));

    when(basicValidator.addPreprepareMessage(any())).thenReturn(false);

    assertThat(validator.validateMessage(msg)).isFalse();
    verify(validatorFactory, times(1))
        .createAt(
            prepareCertificate
                .getIbftPrePrepareMessage()
                .getUnsignedMessageData()
                .getRoundIdentifier());
    verify(basicValidator, times(1))
        .addPreprepareMessage(prepareCertificate.getIbftPrePrepareMessage());
    verify(basicValidator, never()).validatePrepareMessage(any());
    verify(basicValidator, never()).validateCommmitMessage(any());
  }

  @Test
  public void roundChangeContainingValidPreprepareButNoPrepareMessagesFails() {
    final IbftPreparedCertificate prepareCertificate =
        new IbftPreparedCertificate(
            proposerMessageFactory.createIbftSignedPrePrepareMessageData(currentRound, block),
            Collections.emptyList());

    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.of(prepareCertificate));

    when(basicValidator.addPreprepareMessage(any())).thenReturn(true);
    assertThat(validator.validateMessage(msg)).isFalse();
  }

  @Test
  public void roundChangeInvalidPrepareMessageFromProposerFails() {
    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        validatorMessageFactory.createIbftSignedPrepareMessageData(currentRound, block.getHash());
    final IbftPreparedCertificate prepareCertificate =
        new IbftPreparedCertificate(
            proposerMessageFactory.createIbftSignedPrePrepareMessageData(currentRound, block),
            Lists.newArrayList(prepareMsg));

    when(basicValidator.addPreprepareMessage(any())).thenReturn(true);
    when(basicValidator.validatePrepareMessage(any())).thenReturn(false);

    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.of(prepareCertificate));

    assertThat(validator.validateMessage(msg)).isFalse();

    verify(basicValidator, times(1)).validatePrepareMessage(prepareMsg);
    verify(basicValidator, never()).validateCommmitMessage(any());
  }

  @Test
  public void roundChangeWithDifferentSequenceNumberFails() {
    final ConsensusRoundIdentifier latterRoundIdentifier =
        new ConsensusRoundIdentifier(currentRound.getSequenceNumber() + 1, 1);

    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            latterRoundIdentifier, Optional.empty());

    assertThat(validator.validateMessage(msg)).isFalse();
    verify(basicValidator, never()).validatePrepareMessage(any());
  }

  @Test
  public void roundChangeWithPreprepareFromARoundAheadOfRoundChangeTargetFails() {
    final ConsensusRoundIdentifier futureRound =
        new ConsensusRoundIdentifier(
            currentRound.getSequenceNumber(), currentRound.getRoundNumber() + 2);

    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        validatorMessageFactory.createIbftSignedPrepareMessageData(futureRound, block.getHash());
    final IbftPreparedCertificate prepareCertificate =
        new IbftPreparedCertificate(
            proposerMessageFactory.createIbftSignedPrePrepareMessageData(futureRound, block),
            Lists.newArrayList(prepareMsg));

    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.of(prepareCertificate));

    assertThat(validator.validateMessage(msg)).isFalse();
    verify(validatorFactory, never()).createAt(any());
    verify(basicValidator, never()).validatePrepareMessage(prepareMsg);
    verify(basicValidator, never()).validateCommmitMessage(any());
  }

  @Test
  public void roudnChangeWithPastPreprepareForCurrentHeightIsSuccessful() {
    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        validatorMessageFactory.createIbftSignedPrepareMessageData(currentRound, block.getHash());
    final IbftPreparedCertificate prepareCertificate =
        new IbftPreparedCertificate(
            proposerMessageFactory.createIbftSignedPrePrepareMessageData(currentRound, block),
            Lists.newArrayList(prepareMsg));

    final IbftSignedMessageData<IbftUnsignedRoundChangeMessageData> msg =
        proposerMessageFactory.createIbftSignedRoundChangeMessageData(
            targetRound, Optional.of(prepareCertificate));

    when(basicValidator.addPreprepareMessage(prepareCertificate.getIbftPrePrepareMessage()))
        .thenReturn(true);
    when(basicValidator.validatePrepareMessage(prepareMsg)).thenReturn(true);

    assertThat(validator.validateMessage(msg)).isTrue();
    verify(validatorFactory, times(1))
        .createAt(
            prepareCertificate
                .getIbftPrePrepareMessage()
                .getUnsignedMessageData()
                .getRoundIdentifier());
    verify(basicValidator, times(1))
        .addPreprepareMessage(prepareCertificate.getIbftPrePrepareMessage());
    verify(basicValidator, times(1)).validatePrepareMessage(prepareMsg);
  }
}
