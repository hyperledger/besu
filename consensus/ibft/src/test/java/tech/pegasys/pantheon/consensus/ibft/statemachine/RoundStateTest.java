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
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;

import java.math.BigInteger;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RoundStateTest {

  private final List<KeyPair> validatorKeys = Lists.newArrayList();
  private final List<MessageFactory> validatorMessageFactories = Lists.newArrayList();
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 1);

  private final List<Address> validators = Lists.newArrayList();

  @Mock private MessageValidator messageValidator;

  @Mock private Block block;

  @Before
  public void setup() {
    for (int i = 0; i < 3; i++) {
      final KeyPair newKeyPair = KeyPair.generate();
      validatorKeys.add(newKeyPair);
      validators.add(Util.publicKeyToAddress(newKeyPair.getPublicKey()));
      validatorMessageFactories.add(new MessageFactory(newKeyPair));
    }
    when(block.getHash()).thenReturn(Hash.fromHexStringLenient("1"));
  }

  @Test
  public void defaultRoundIsNotPreparedOrCommittedAndHasNoPreparedCertificate() {
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isEmpty();
  }

  @Test
  public void ifProposalMessageFailsValidationMethodReturnsFalse() {
    when(messageValidator.addSignedProposalPayload(any())).thenReturn(false);
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);

    assertThat(roundState.setProposedBlock(proposal)).isFalse();
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isEmpty();
  }

  @Test
  public void singleValidatorIsPreparedWithJustProposal() {
    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);

    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isNotEmpty();
    assertThat(roundState.constructPreparedCertificate().get().getProposalPayload())
        .isEqualTo(proposal);
  }

  @Test
  public void singleValidatorRequiresCommitMessageToBeCommitted() {
    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validateCommmitMessage(any())).thenReturn(true);

    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);

    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();

    final SignedData<CommitPayload> commit =
        validatorMessageFactories
            .get(0)
            .createSignedCommitPayload(
                roundIdentifier,
                block.getHash(),
                Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 1));

    roundState.addCommitSeal(commit);
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isTrue();
    assertThat(roundState.constructPreparedCertificate()).isNotEmpty();
  }

  @Test
  public void prepareMessagesCanBeReceivedPriorToProposal() {
    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validatePrepareMessage(any())).thenReturn(true);

    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);

    final SignedData<PreparePayload> firstPrepare =
        validatorMessageFactories
            .get(1)
            .createSignedPreparePayload(roundIdentifier, block.getHash());

    final SignedData<PreparePayload> secondPrepare =
        validatorMessageFactories
            .get(2)
            .createSignedPreparePayload(roundIdentifier, block.getHash());

    roundState.addPreparedPeer(firstPrepare);
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isEmpty();

    roundState.addPreparedPeer(secondPrepare);
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isEmpty();

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);
    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isNotEmpty();
  }

  @Test
  public void invalidPriorPrepareMessagesAreDiscardedUponSubsequentProposal() {
    final SignedData<PreparePayload> firstPrepare =
        validatorMessageFactories
            .get(1)
            .createSignedPreparePayload(roundIdentifier, block.getHash());

    final SignedData<PreparePayload> secondPrepare =
        validatorMessageFactories
            .get(2)
            .createSignedPreparePayload(roundIdentifier, block.getHash());

    // RoundState has a quorum size of 3, meaning 1 proposal and 2 prepare are required to be
    // 'prepared'.
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);

    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validatePrepareMessage(firstPrepare)).thenReturn(true);
    when(messageValidator.validatePrepareMessage(secondPrepare)).thenReturn(false);

    roundState.addPreparedPeer(firstPrepare);
    roundState.addPreparedPeer(secondPrepare);
    verify(messageValidator, never()).validatePrepareMessage(any());

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);

    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedCertificate()).isEmpty();
  }

  @Test
  public void prepareMessageIsValidatedAgainstExitingProposal() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);

    final SignedData<PreparePayload> firstPrepare =
        validatorMessageFactories
            .get(1)
            .createSignedPreparePayload(roundIdentifier, block.getHash());

    final SignedData<PreparePayload> secondPrepare =
        validatorMessageFactories
            .get(2)
            .createSignedPreparePayload(roundIdentifier, block.getHash());

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);

    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validatePrepareMessage(firstPrepare)).thenReturn(false);
    when(messageValidator.validatePrepareMessage(secondPrepare)).thenReturn(true);

    roundState.setProposedBlock(proposal);
    assertThat(roundState.isPrepared()).isFalse();

    roundState.addPreparedPeer(firstPrepare);
    assertThat(roundState.isPrepared()).isFalse();

    roundState.addPreparedPeer(secondPrepare);
    assertThat(roundState.isPrepared()).isTrue();
  }

  @Test
  public void commitSealsAreExtractedFromReceivedMessages() {
    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validateCommmitMessage(any())).thenReturn(true);

    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);

    final SignedData<CommitPayload> firstCommit =
        validatorMessageFactories
            .get(1)
            .createSignedCommitPayload(
                roundIdentifier,
                block.getHash(),
                Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 1));

    final SignedData<CommitPayload> secondCommit =
        validatorMessageFactories
            .get(2)
            .createSignedCommitPayload(
                roundIdentifier,
                block.getHash(),
                Signature.create(BigInteger.TEN, BigInteger.TEN, (byte) 1));

    final SignedData<ProposalPayload> proposal =
        validatorMessageFactories.get(0).createSignedProposalPayload(roundIdentifier, block);

    roundState.setProposedBlock(proposal);
    roundState.addCommitSeal(firstCommit);
    assertThat(roundState.isCommitted()).isFalse();
    roundState.addCommitSeal(secondCommit);
    assertThat(roundState.isCommitted()).isTrue();

    assertThat(roundState.getCommitSeals())
        .containsOnly(
            firstCommit.getPayload().getCommitSeal(), secondCommit.getPayload().getCommitSeal());
  }
}
