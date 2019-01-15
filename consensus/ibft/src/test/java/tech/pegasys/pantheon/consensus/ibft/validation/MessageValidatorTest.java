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
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MessageValidatorTest {

  private final KeyPair proposerKey = KeyPair.generate();
  private final KeyPair validatorKey = KeyPair.generate();
  private final KeyPair nonValidatorKey = KeyPair.generate();
  private final MessageFactory proposerMessageFactory = new MessageFactory(proposerKey);
  private final MessageFactory validatorMessageFactory = new MessageFactory(validatorKey);
  private final MessageFactory nonValidatorMessageFactory = new MessageFactory(nonValidatorKey);

  private final List<Address> validators = Lists.newArrayList();

  @Mock private BlockHeaderValidator<IbftContext> headerValidator;
  private final BlockHeader parentHeader = mock(BlockHeader.class);
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(2, 0);
  private MessageValidator validator;

  private final Block block = mock(Block.class);

  @Before
  public void setup() {
    validators.add(Util.publicKeyToAddress(proposerKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validatorKey.getPublicKey()));

    final ProtocolContext<IbftContext> protocolContext =
        new ProtocolContext<>(
            mock(MutableBlockchain.class), mock(WorldStateArchive.class), mock(IbftContext.class));

    validator =
        new MessageValidator(
            validators,
            Util.publicKeyToAddress(proposerKey.getPublicKey()),
            roundIdentifier,
            headerValidator,
            protocolContext,
            parentHeader);

    when(block.getHash()).thenReturn(Hash.fromHexStringLenient("1"));
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);
    insertRoundToBlockHeader(0);
  }

  private void insertRoundToBlockHeader(final int round) {
    final IbftExtraData extraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            Collections.emptyList(),
            Optional.empty(),
            round,
            validators);
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getExtraData()).thenReturn(extraData.encode());
    when(block.getHeader()).thenReturn(header);
  }

  @Test
  public void receivingAPrepareMessageBeforePrePrepareFails() {
    final SignedData<PreparePayload> prepareMsg =
        proposerMessageFactory.createSignedPreparePayload(roundIdentifier, Hash.ZERO);

    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
  }

  @Test
  public void receivingACommitMessageBeforePreprepareFails() {
    final SignedData<CommitPayload> commitMsg =
        proposerMessageFactory.createSignedCommitPayload(
            roundIdentifier, Hash.ZERO, SECP256K1.sign(Hash.ZERO, proposerKey));

    assertThat(validator.validateCommmitMessage(commitMsg)).isFalse();
  }

  @Test
  public void receivingPreprepareMessageFromNonProposerFails() {
    final SignedData<ProposalPayload> preprepareMsg =
        validatorMessageFactory.createSignedProposalPayload(roundIdentifier, mock(Block.class));

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isFalse();
  }

  @Test
  public void receivingPreprepareMessageWithIllegalBlockFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(false);
    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, mock(Block.class));

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isFalse();
  }

  @Test
  public void receivingPrepareFromProposerFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);

    final SignedData<PreparePayload> prepareMsg =
        proposerMessageFactory.createSignedPreparePayload(roundIdentifier, block.getHash());

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
  }

  @Test
  public void receivingPrepareFromNonValidatorFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);

    final SignedData<PreparePayload> prepareMsg =
        nonValidatorMessageFactory.createSignedPreparePayload(roundIdentifier, block.getHash());

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
  }

  @Test
  public void receivingMessagesWithDifferentRoundIdFromPreprepareFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);

    final ConsensusRoundIdentifier invalidRoundIdentifier =
        new ConsensusRoundIdentifier(
            roundIdentifier.getSequenceNumber(), roundIdentifier.getRoundNumber() + 1);
    final SignedData<PreparePayload> prepareMsg =
        validatorMessageFactory.createSignedPreparePayload(invalidRoundIdentifier, block.getHash());
    final SignedData<CommitPayload> commitMsg =
        validatorMessageFactory.createSignedCommitPayload(
            invalidRoundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), proposerKey));

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
    assertThat(validator.validateCommmitMessage(commitMsg)).isFalse();
  }

  @Test
  public void receivingPrepareNonProposerValidatorWithCorrectRoundIsSuccessful() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);
    final SignedData<PreparePayload> prepareMsg =
        validatorMessageFactory.createSignedPreparePayload(roundIdentifier, block.getHash());

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isTrue();
  }

  @Test
  public void receivingACommitMessageWithAnInvalidCommitSealFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);

    final SignedData<CommitPayload> commitMsg =
        proposerMessageFactory.createSignedCommitPayload(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), nonValidatorKey));

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();
    assertThat(validator.validateCommmitMessage(commitMsg)).isFalse();
  }

  @Test
  public void commitMessageContainingValidSealFromValidatorIsSuccessful() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);

    final SignedData<CommitPayload> proposerCommitMsg =
        proposerMessageFactory.createSignedCommitPayload(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), proposerKey));

    final SignedData<CommitPayload> validatorCommitMsg =
        validatorMessageFactory.createSignedCommitPayload(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), validatorKey));

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();
    assertThat(validator.validateCommmitMessage(proposerCommitMsg)).isTrue();
    assertThat(validator.validateCommmitMessage(validatorCommitMsg)).isTrue();
  }

  @Test
  public void subsequentPreprepareHasDifferentSenderFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);
    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();

    final SignedData<ProposalPayload> secondPreprepareMsg =
        validatorMessageFactory.createSignedProposalPayload(roundIdentifier, block);
    assertThat(validator.addSignedProposalPayload(secondPreprepareMsg)).isFalse();
  }

  @Test
  public void subsequentPreprepareHasDifferentContentFails() {

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);
    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();

    final ConsensusRoundIdentifier newRoundIdentifier = new ConsensusRoundIdentifier(3, 0);
    final SignedData<ProposalPayload> secondPreprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(newRoundIdentifier, block);
    assertThat(validator.addSignedProposalPayload(secondPreprepareMsg)).isFalse();
  }

  @Test
  public void subsequentPreprepareHasIdenticalSenderAndContentIsSuccessful() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);
    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isTrue();

    final SignedData<ProposalPayload> secondPreprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);
    assertThat(validator.addSignedProposalPayload(secondPreprepareMsg)).isTrue();
  }

  @Test
  public void blockRoundMisMatchWithMessageRoundFails() {
    insertRoundToBlockHeader(roundIdentifier.getRoundNumber() + 1);

    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);
    final SignedData<ProposalPayload> preprepareMsg =
        proposerMessageFactory.createSignedProposalPayload(roundIdentifier, block);

    assertThat(validator.addSignedProposalPayload(preprepareMsg)).isFalse();
  }
}
