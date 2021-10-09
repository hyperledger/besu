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
package org.hyperledger.besu.consensus.ibft.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.payload.PreparePayload;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidator;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Util;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RoundStateTest {

  private final List<NodeKey> validatorKeys = Lists.newArrayList();
  private final List<MessageFactory> validatorMessageFactories = Lists.newArrayList();
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 1);

  private final List<Address> validators = Lists.newArrayList();

  @Mock private MessageValidator messageValidator;

  @Mock private Block block;

  @Before
  public void setup() {
    for (int i = 0; i < 3; i++) {
      final NodeKey newNodeKey = NodeKeyUtils.generate();
      validatorKeys.add(newNodeKey);
      validators.add(Util.publicKeyToAddress(newNodeKey.getPublicKey()));
      validatorMessageFactories.add(new MessageFactory(newNodeKey));
    }
    when(block.getHash()).thenReturn(Hash.fromHexStringLenient("1"));
  }

  @Test
  public void defaultRoundIsNotPreparedOrCommittedAndHasNoPreparedCertificate() {
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isEmpty();
  }

  @Test
  public void ifProposalMessageFailsValidationMethodReturnsFalse() {
    when(messageValidator.validateProposal(any())).thenReturn(false);
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());

    assertThat(roundState.setProposedBlock(proposal)).isFalse();
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isEmpty();
  }

  @Test
  public void singleValidatorIsPreparedWithJustProposal() {
    when(messageValidator.validateProposal(any())).thenReturn(true);
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());
    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isNotEmpty();
    assertThat(
            roundState
                .constructPreparedRoundArtifacts()
                .get()
                .getPreparedCertificate()
                .getProposalPayload())
        .isEqualTo(proposal.getSignedPayload());
  }

  @Test
  public void singleValidatorRequiresCommitMessageToBeCommitted() {
    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validateCommit(any())).thenReturn(true);

    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());

    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();

    final Commit commit =
        validatorMessageFactories
            .get(0)
            .createCommit(
                roundIdentifier,
                block.getHash(),
                SignatureAlgorithmFactory.getInstance()
                    .createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 1));

    roundState.addCommitMessage(commit);
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isTrue();
    assertThat(roundState.constructPreparedRoundArtifacts()).isNotEmpty();
  }

  @Test
  public void prepareMessagesCanBeReceivedPriorToProposal() {
    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validatePrepare(any())).thenReturn(true);

    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);

    final Prepare firstPrepare =
        validatorMessageFactories.get(1).createPrepare(roundIdentifier, block.getHash());

    final Prepare secondPrepare =
        validatorMessageFactories.get(2).createPrepare(roundIdentifier, block.getHash());

    roundState.addPrepareMessage(firstPrepare);
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isEmpty();

    roundState.addPrepareMessage(secondPrepare);
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isEmpty();

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());
    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isNotEmpty();
  }

  @Test
  public void invalidPriorPrepareMessagesAreDiscardedUponSubsequentProposal() {
    final Prepare firstPrepare =
        validatorMessageFactories.get(1).createPrepare(roundIdentifier, block.getHash());

    final Prepare secondPrepare =
        validatorMessageFactories.get(2).createPrepare(roundIdentifier, block.getHash());

    // RoundState has a quorum size of 3, meaning 1 proposal and 2 prepare are required to be
    // 'prepared'.
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);

    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validatePrepare(firstPrepare)).thenReturn(true);
    when(messageValidator.validatePrepare(secondPrepare)).thenReturn(false);

    roundState.addPrepareMessage(firstPrepare);
    roundState.addPrepareMessage(secondPrepare);
    verify(messageValidator, never()).validatePrepare(any());

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());

    assertThat(roundState.setProposedBlock(proposal)).isTrue();
    assertThat(roundState.isPrepared()).isFalse();
    assertThat(roundState.isCommitted()).isFalse();
    assertThat(roundState.constructPreparedRoundArtifacts()).isEmpty();
  }

  @Test
  public void prepareMessageIsValidatedAgainstExitingProposal() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);

    final Prepare firstPrepare =
        validatorMessageFactories.get(1).createPrepare(roundIdentifier, block.getHash());

    final Prepare secondPrepare =
        validatorMessageFactories.get(2).createPrepare(roundIdentifier, block.getHash());

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());

    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validatePrepare(firstPrepare)).thenReturn(false);
    when(messageValidator.validatePrepare(secondPrepare)).thenReturn(true);

    roundState.setProposedBlock(proposal);
    assertThat(roundState.isPrepared()).isFalse();

    roundState.addPrepareMessage(firstPrepare);
    assertThat(roundState.isPrepared()).isFalse();

    roundState.addPrepareMessage(secondPrepare);
    assertThat(roundState.isPrepared()).isTrue();
  }

  @Test
  public void commitSealsAreExtractedFromReceivedMessages() {
    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validateCommit(any())).thenReturn(true);

    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

    final Commit firstCommit =
        validatorMessageFactories
            .get(1)
            .createCommit(
                roundIdentifier,
                block.getHash(),
                signatureAlgorithm.createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 1));

    final Commit secondCommit =
        validatorMessageFactories
            .get(2)
            .createCommit(
                roundIdentifier,
                block.getHash(),
                signatureAlgorithm.createSignature(BigInteger.TEN, BigInteger.TEN, (byte) 1));

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());

    roundState.setProposedBlock(proposal);
    roundState.addCommitMessage(firstCommit);
    assertThat(roundState.isCommitted()).isFalse();
    roundState.addCommitMessage(secondCommit);
    assertThat(roundState.isCommitted()).isTrue();

    assertThat(roundState.getCommitSeals())
        .containsOnly(firstCommit.getCommitSeal(), secondCommit.getCommitSeal());
  }

  @Test
  public void duplicatePreparesAreNotIncludedInRoundChangeMessage() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);

    final Prepare firstPrepare =
        validatorMessageFactories.get(1).createPrepare(roundIdentifier, block.getHash());

    final Prepare secondPrepare =
        validatorMessageFactories.get(2).createPrepare(roundIdentifier, block.getHash());

    final Prepare duplicatePrepare =
        validatorMessageFactories.get(2).createPrepare(roundIdentifier, block.getHash());

    final Proposal proposal =
        validatorMessageFactories.get(0).createProposal(roundIdentifier, block, Optional.empty());

    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validatePrepare(any())).thenReturn(true);

    roundState.setProposedBlock(proposal);
    roundState.addPrepareMessage(firstPrepare);
    roundState.addPrepareMessage(secondPrepare);
    roundState.addPrepareMessage(duplicatePrepare);

    assertThat(roundState.isPrepared()).isTrue();
    assertThat(roundState.isCommitted()).isFalse();

    final Optional<PreparedRoundArtifacts> preparedRoundArtifacts =
        roundState.constructPreparedRoundArtifacts();
    assertThat(preparedRoundArtifacts).isNotEmpty();
    assertThat(preparedRoundArtifacts.get().getBlock()).isEqualTo(block);

    final List<SignedData<PreparePayload>> expectedPrepares =
        List.of(firstPrepare, secondPrepare).stream()
            .map(BftMessage::getSignedPayload)
            .collect(Collectors.toList());
    assertThat(preparedRoundArtifacts.get().getPreparedCertificate().getPreparePayloads())
        .isEqualTo(expectedPrepares);
  }
}
